package cl.duoc.cloud.clientes;

import cl.duoc.cloud.clientes.config.ApiErrorHandler.*;
import cl.duoc.cloud.clientes.model.*;
import cl.duoc.cloud.clientes.repo.ClientesRepository;
import cl.duoc.cloud.clientes.service.ClientesService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(GestionClientesTests.Config.class)
class GestionClientesTests {
    @Configuration @EnableTransactionManagement
    @Import({ClientesRepository.class, ClientesService.class})
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:gestion_clientes;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager txManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }

    @Autowired ClientesService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach void preparar() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas(cuenta_id BIGINT PRIMARY KEY,nombre VARCHAR(150) NOT NULL,saldo DECIMAL(15,2) NOT NULL,edad INT NOT NULL,tipo_cuenta VARCHAR(30) NOT NULL,fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        try (var conexion = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("03-gestion-cuentas.sql"));
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("04-clientes.sql"));
        }
        jdbc.update("DELETE FROM clientes_cuentas");
        jdbc.update("DELETE FROM clientes");
        jdbc.update("DELETE FROM cuentas_estado");
        jdbc.update("DELETE FROM cuentas");
        jdbc.update("INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo_cuenta) VALUES(101,'Jane Smith',5000,45,'ahorro'),(102,'Juan',8000,30,'corriente'),(103,'Nueva',0,30,'ahorro'),(104,'Cerrada',0,30,'ahorro')");
        jdbc.update("INSERT INTO cuentas_estado(cuenta_id,estado,fecha_cierre) VALUES(104,'CERRADA',CURRENT_TIMESTAMP)");
    }

    private CrearClienteRequest crear(Long id, Long cuenta) {
        return new CrearClienteRequest(id, cuenta, "Jane Smith", "JANE@EXAMPLE.COM", "+56911111111", "Santiago", "ESTANDAR");
    }

    private ActualizarClienteRequest actualizar(String nombre, int version) {
        return new ActualizarClienteRequest(nombre, "perfil@example.com", "+56922222222", "Maipú", "PREFERENTE", version);
    }

    @Test void creacionYConsultaPorCuentaConservanDatosYSaldo() {
        var creado = service.crear(crear(900101L, 101L));
        var consultado = service.consultarPorCuenta(101L);
        assertEquals(creado, consultado);
        assertEquals("jane@example.com", consultado.email());
        assertEquals("ESTANDAR", consultado.perfil());
        assertEquals(0, consultado.version());
        assertEquals(List.of(101L), consultado.cuentas());
        assertNotNull(consultado.fechaActualizacion());
        assertEquals(0, new BigDecimal("5000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuentas WHERE cuenta_id=101", BigDecimal.class)));
    }

    @Test void mantenimientoPersistenteRechazaVersionAntiguaSinSobrescribir() {
        service.crear(crear(900101L, 101L));
        var actualizado = service.actualizar(900101L, actualizar("Jane Actualizada", 0));
        assertEquals(1, actualizado.version());
        assertEquals("PREFERENTE", actualizado.perfil());
        assertEquals("Maipú", actualizado.direccion());
        assertThrows(ClienteConflicto.class, () -> service.actualizar(900101L, actualizar("Dato antiguo", 0)));
        assertEquals(actualizado, service.consultar(900101L));
        assertEquals(List.of(101L), actualizado.cuentas());
        assertEquals(0, new BigDecimal("5000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuentas WHERE cuenta_id=101", BigDecimal.class)));
    }

    @Test void cuentaDuplicadaRevierteLaCreacionDelSegundoCliente() {
        service.crear(crear(900101L, 101L));
        assertThrows(ClienteConflicto.class, () -> service.crear(crear(900102L, 101L)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM clientes_cuentas", Integer.class));
        assertThrows(ClienteNoEncontrado.class, () -> service.consultar(900102L));
        assertEquals(900101L, service.consultarPorCuenta(101L).clienteId());
    }

    @Test void clienteDuplicadoNoReasignaUnaCuentaDistinta() {
        service.crear(crear(900101L, 101L));
        assertThrows(ClienteConflicto.class, () -> service.crear(crear(900101L, 102L)));
        assertEquals(List.of(101L), service.consultar(900101L).cuentas());
        assertThrows(ClienteNoEncontrado.class, () -> service.consultarPorCuenta(102L));
    }

    @Test void variasCuentasYVinculacionRepetidaConservanUnTitularPorCuenta() {
        service.crear(crear(900101L, 101L));
        assertEquals(List.of(101L, 102L), service.vincular(900101L, 102L).cuentas());
        assertEquals(List.of(101L, 102L), service.vincular(900101L, 102L).cuentas());
        service.crear(crear(900102L, 103L));
        assertThrows(ClienteConflicto.class, () -> service.vincular(900102L, 102L));
        assertEquals(900101L, service.consultarPorCuenta(102L).clienteId());
        assertEquals(List.of(103L), service.consultar(900102L).cuentas());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM clientes_cuentas", Integer.class));
    }

    @Test void cuentaCerradaNoSeVinculaNiCreaClientesHuerfanos() {
        assertThrows(ClienteConflicto.class, () -> service.crear(crear(900101L, 104L)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class));
        service.crear(crear(900101L, 101L));
        assertThrows(ClienteConflicto.class, () -> service.vincular(900101L, 104L));
        assertEquals(List.of(101L), service.consultar(900101L).cuentas());
        assertEquals("CERRADA", jdbc.queryForObject("SELECT estado FROM cuentas_estado WHERE cuenta_id=104", String.class));
    }

    @Test void clienteOCuentaInexistentesProducen404DeNegocio() {
        assertThrows(ClienteNoEncontrado.class, () -> service.crear(crear(900101L, 999L)));
        assertThrows(ClienteNoEncontrado.class, () -> service.consultar(900101L));
        assertThrows(ClienteNoEncontrado.class, () -> service.consultarPorCuenta(101L));
        assertThrows(ClienteNoEncontrado.class, () -> service.actualizar(900101L, actualizar("Jane", 0)));
        assertThrows(ClienteNoEncontrado.class, () -> service.vincular(900101L, 101L));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM clientes", Integer.class));
    }

    @Test void dosActualizacionesConcurrentesNoPierdenCambiosEnSilencio() throws Exception {
        service.crear(crear(900101L, 101L));
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var primera = pool.submit(() -> competir(inicio, "Primera"));
            var segunda = pool.submit(() -> competir(inicio, "Segunda"));
            inicio.countDown();
            boolean resultado1 = primera.get(5, TimeUnit.SECONDS);
            boolean resultado2 = segunda.get(5, TimeUnit.SECONDS);
            assertNotEquals(resultado1, resultado2);
            var actual = service.consultar(900101L);
            assertEquals(1, actual.version());
            assertEquals(resultado1 ? "Primera" : "Segunda", actual.nombre());
            assertEquals(List.of(101L), actual.cuentas());
        } finally {
            inicio.countDown();
            pool.shutdownNow();
        }
    }

    private boolean competir(CountDownLatch inicio, String nombre) throws InterruptedException {
        if (!inicio.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("La prueba no inició");
        try {
            service.actualizar(900101L, actualizar(nombre, 0));
            return true;
        } catch (ClienteConflicto ex) {
            return false;
        }
    }
}
