package cl.duoc.cloud.cuentas;

import cl.duoc.cloud.cuentas.config.ApiErrorHandler.*;
import cl.duoc.cloud.cuentas.model.*;
import cl.duoc.cloud.cuentas.repo.CuentasRepository;
import cl.duoc.cloud.cuentas.service.CuentasService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(GestionCuentasTests.Config.class)
class GestionCuentasTests {
    @Configuration @EnableTransactionManagement
    @Import({CuentasRepository.class, CuentasService.class})
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:gestion_cuentas;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager txManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }

    @Autowired CuentasService service;
    @Autowired CuentasRepository repo;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSourceTransactionManager txManager;

    @BeforeEach void preparar() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas(cuenta_id BIGINT PRIMARY KEY,nombre VARCHAR(150) NOT NULL,saldo DECIMAL(15,2) NOT NULL,edad INT NOT NULL,tipo_cuenta VARCHAR(30) NOT NULL,fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas_estado(cuenta_id BIGINT PRIMARY KEY REFERENCES cuentas(cuenta_id),estado VARCHAR(10) NOT NULL,fecha_apertura TIMESTAMP DEFAULT CURRENT_TIMESTAMP,fecha_cierre TIMESTAMP)");
        jdbc.update("DELETE FROM cuentas_estado"); jdbc.update("DELETE FROM cuentas");
    }

    private AperturaCuentaRequest solicitud() { return new AperturaCuentaRequest(900001L, "Prueba", 30, "ahorro"); }

    @Test void aperturaMantenimientoCierreConservanCuentaYSaldo() {
        assertEquals("ACTIVA", service.abrir(solicitud()).estado());
        assertEquals(0, repo.cuenta(900001L).orElseThrow().saldo().signum());
        assertEquals("corriente", service.mantener(900001L,new MantenimientoCuentaRequest("corriente")).tipoCuenta());
        assertEquals("CERRADA", service.cerrar(900001L).estado());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cuentas",Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT fecha_cierre FROM cuentas_estado",java.sql.Timestamp.class));
    }

    @Test void duplicadoNoSobrescribeUnaCuentaExistente() {
        service.abrir(solicitud());
        jdbc.update("UPDATE cuentas SET saldo=1000 WHERE cuenta_id=900001");
        assertThrows(ConflictoCuenta.class, () -> service.abrir(solicitud()));
        assertEquals(0,new BigDecimal("1000").compareTo(repo.cuenta(900001L).orElseThrow().saldo()));
    }

    @Test void saldoDistintoDeCeroImpideCierre() {
        service.abrir(solicitud());
        jdbc.update("UPDATE cuentas SET saldo=1000 WHERE cuenta_id=900001");
        assertThrows(ConflictoCuenta.class, () -> service.cerrar(900001L));
        assertEquals("ACTIVA",repo.cuenta(900001L).orElseThrow().estado());
    }

    @Test void cuentaCerradaNoSeModificaNiSeReabreYElCierreEsRepetible() {
        service.abrir(solicitud()); service.cerrar(900001L);
        var fecha=jdbc.queryForObject("SELECT fecha_cierre FROM cuentas_estado",java.sql.Timestamp.class);
        assertEquals("CERRADA",service.cerrar(900001L).estado());
        assertEquals(fecha,jdbc.queryForObject("SELECT fecha_cierre FROM cuentas_estado",java.sql.Timestamp.class));
        assertThrows(ConflictoCuenta.class, () -> service.mantener(900001L,new MantenimientoCuentaRequest("corriente")));
        assertThrows(ConflictoCuenta.class, () -> service.abrir(solicitud()));
    }

    @Test void cuentaLegacySinEstadoSeConsideraActivaYPuedeCerrarse() {
        jdbc.update("INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo_cuenta) VALUES(102,'Legacy',0,35,'ahorro')");
        assertEquals("ACTIVA",repo.cuenta(102L).orElseThrow().estado());
        assertEquals("CERRADA",service.cerrar(102L).estado());
    }

    @Test void cuentaInexistenteDevuelveErrorDeNegocio() {
        assertThrows(RecursoNoEncontrado.class, () -> service.cerrar(900001L));
        assertThrows(RecursoNoEncontrado.class, () -> service.mantener(900001L,new MantenimientoCuentaRequest("ahorro")));
    }

    @Test void cierreEsperaElCommitDeLaOperacionQueBloqueaLaCuenta() throws Exception {
        service.abrir(solicitud());
        jdbc.update("UPDATE cuentas SET saldo=1000 WHERE cuenta_id=900001");
        var bloqueada=new CountDownLatch(1);
        var liberar=new CountDownLatch(1);
        var inicioCierre=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var retiro=pool.submit(() -> new TransactionTemplate(txManager).execute(status -> {
                repo.saldoParaActualizar(900001L); bloqueada.countDown();
                try { if(!liberar.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la prueba"); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                jdbc.update("UPDATE cuentas SET saldo=0 WHERE cuenta_id=900001"); return true;
            }));
            assertTrue(bloqueada.await(5,TimeUnit.SECONDS));
            var cierre=pool.submit(() -> { inicioCierre.countDown(); return service.cerrar(900001L); });
            assertTrue(inicioCierre.await(5,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> cierre.get(150,TimeUnit.MILLISECONDS));
            liberar.countDown(); assertTrue(retiro.get(5,TimeUnit.SECONDS));
            assertEquals("CERRADA",cierre.get(5,TimeUnit.SECONDS).estado());
            assertEquals(0,repo.cuenta(900001L).orElseThrow().saldo().signum());
        } finally { liberar.countDown(); pool.shutdownNow(); }
    }
}
