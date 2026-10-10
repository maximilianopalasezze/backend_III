package cl.duoc.cloud.movimientos;

import cl.duoc.cloud.movimientos.event.*;
import cl.duoc.cloud.movimientos.repo.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(MovimientosPagosTests.Config.class)
class MovimientosPagosTests {
    @Configuration @EnableTransactionManagement
    @Import({MovimientosRepository.class, MovimientosPagosRepository.class, PagoProcesadoConsumer.class})
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:movimientos_pagos;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager manager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
    @Autowired PagoProcesadoConsumer consumer;
    @Autowired MovimientosRepository repo;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource ds;

    @BeforeEach void preparar() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas(cuenta_id BIGINT PRIMARY KEY,nombre VARCHAR(150) NOT NULL,saldo DECIMAL(15,2) NOT NULL,edad INT NOT NULL,tipo_cuenta VARCHAR(30) NOT NULL,fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS movimientos_anuales_procesados(id BIGINT AUTO_INCREMENT PRIMARY KEY,cuenta_id BIGINT NOT NULL,fecha DATE NOT NULL,tipo_movimiento VARCHAR(50),monto DECIMAL(15,2),descripcion VARCHAR(255),archivo_origen VARCHAR(255))");
        try (var conexion = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("03-gestion-cuentas.sql"));
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("05-pagos.sql"));
        }
        jdbc.update("DELETE FROM movimientos_pagos");
        jdbc.update("DELETE FROM movimientos_anuales_procesados");
        jdbc.update("DELETE FROM cuentas");
        jdbc.update("INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo_cuenta) VALUES(101,'Origen',5000,30,'ahorro'),(102,'Destino',8000,30,'corriente')");
    }

    private PagoProcesadoEvent evento(String tipo, String referencia, Long destino, String valor) {
        var monto = new BigDecimal(valor);
        var antes = new BigDecimal("5000");
        var despues = tipo.equals("DEPOSITO") ? antes.add(monto) : antes.subtract(monto);
        return new PagoProcesadoEvent("solicitud-001", referencia, tipo, 101L, destino, monto, "Beneficiario", "Concepto",
                antes, despues, destino == null ? null : new BigDecimal("8000"),
                destino == null ? null : new BigDecimal("8000").add(monto), "APROBADA", LocalDateTime.of(2026,10,10,19,50));
    }
    private int contar() { return jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_pagos", Integer.class); }
    private String referencia() { return UUID.randomUUID().toString(); }

    @Test void depositoYPagoGeneranMovimientosSinCambiarSaldosMaestros() {
        consumer.consumir(evento("DEPOSITO", referencia(), null, "100"));
        consumer.consumir(evento("PAGO", referencia(), null, "100"));
        assertEquals(2, contar());
        assertEquals(0, new BigDecimal("5000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuentas WHERE cuenta_id=101", BigDecimal.class)));
    }

    @Test void transferenciaGeneraSalidaYEntradaEnUnaTransaccion() {
        consumer.consumir(evento("TRANSFERENCIA", referencia(), 102L, "100"));
        assertEquals(2, contar());
        assertEquals("TRANSFERENCIA_SALIDA", jdbc.queryForObject("SELECT tipo_movimiento FROM movimientos_pagos WHERE cuenta_id=101", String.class));
        assertEquals("TRANSFERENCIA_ENTRADA", jdbc.queryForObject("SELECT tipo_movimiento FROM movimientos_pagos WHERE cuenta_id=102", String.class));
        assertEquals(0, new BigDecimal("13000").compareTo(jdbc.queryForObject("SELECT SUM(saldo) FROM cuentas", BigDecimal.class)));
    }

    @Test void reentregaDelMismoEventoNoDuplicaMovimientos() {
        var e = evento("TRANSFERENCIA", referencia(), 102L, "100");
        consumer.consumir(e);
        consumer.consumir(e);
        assertEquals(2, contar());
    }

    @Test void referenciaReutilizadaConOtroMontoNoSobrescribeMovimientos() {
        String ref = referencia();
        consumer.consumir(evento("TRANSFERENCIA", ref, 102L, "100"));
        assertThrows(IllegalArgumentException.class, () -> consumer.consumir(evento("TRANSFERENCIA", ref, 102L, "200")));
        assertEquals(2, contar());
        assertEquals(0, new BigDecimal("200").compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM movimientos_pagos", BigDecimal.class)));
    }

    @Test void falloEnDestinoRevierteTambienElMovimientoDeOrigen() {
        assertThrows(DataAccessException.class, () -> consumer.consumir(evento("TRANSFERENCIA", referencia(), 999L, "100")));
        assertEquals(0, contar());
    }

    @Test void consultaCombinaKafkaYLegadoManteniendoCuentaLimiteYOrden() {
        jdbc.update("INSERT INTO movimientos_anuales_procesados(id,cuenta_id,fecha,tipo_movimiento,monto,descripcion,archivo_origen) VALUES(1,101,'2024-01-01','COMPRA',10,'Legado 1','archivo.csv'),(2,101,'2024-01-01','PAGO',20,'Legado 2','archivo.csv'),(3,102,'2026-10-10','DEPOSITO',999,'Otra cuenta','archivo.csv')");
        String ref = referencia();
        consumer.consumir(evento("DEPOSITO", ref, null, "100"));
        var movimientos = repo.ultimos(101L, 2);
        assertEquals(2, movimientos.size());
        assertEquals("DEPOSITO", movimientos.get(0).tipo());
        assertEquals("kafka:" + ref, movimientos.get(0).archivoOrigen());
        assertEquals("Legado 2", movimientos.get(1).descripcion());
        assertEquals(3, repo.ultimos(101L, 10).size());
    }
}
