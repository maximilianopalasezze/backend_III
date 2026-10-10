package cl.duoc.cloud.pagos;

import cl.duoc.cloud.pagos.config.ApiErrorHandler.*;
import cl.duoc.cloud.pagos.config.TransaccionesConfig;
import cl.duoc.cloud.pagos.model.*;
import cl.duoc.cloud.pagos.repo.*;
import cl.duoc.cloud.pagos.service.*;
import com.fasterxml.jackson.databind.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringJUnitConfig(GestionPagosTests.Config.class)
class GestionPagosTests {
    @Configuration
    @Import({PagosRepository.class, OutboxRepository.class, PagosService.class,
            TransaccionesConfig.class, PublicacionOutboxService.class})
    static class Config {
        @Bean DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:gestion_pagos;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager manager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
        @Bean @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka() { return mock(KafkaTemplate.class); }
    }
    @Autowired PagosService service;
    @Autowired PublicacionOutboxService publicador;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource ds;
    @Autowired ObjectMapper json;

    @BeforeEach void preparar() throws Exception {
        reset(kafka);
        jdbc.execute("CREATE TABLE IF NOT EXISTS cuentas(cuenta_id BIGINT PRIMARY KEY,nombre VARCHAR(150) NOT NULL,saldo DECIMAL(15,2) NOT NULL,edad INT NOT NULL,tipo_cuenta VARCHAR(30) NOT NULL,fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        try (var conexion = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("03-gestion-cuentas.sql"));
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("05-pagos.sql"));
        }
        for (String tabla : List.of("movimientos_pagos", "pagos_outbox", "pagos_operaciones", "cuentas_estado", "cuentas")) jdbc.update("DELETE FROM " + tabla);
        jdbc.update("INSERT INTO cuentas(cuenta_id,nombre,saldo,edad,tipo_cuenta) VALUES(101,'Origen',5000,30,'ahorro'),(102,'Destino',8000,30,'corriente'),(103,'Límite',0,30,'ahorro'),(104,'Cerrada',0,30,'ahorro')");
        jdbc.update("INSERT INTO cuentas_estado(cuenta_id,estado,fecha_cierre) VALUES(104,'CERRADA',CURRENT_TIMESTAMP)");
    }

    private BigDecimal monto(String valor) { return new BigDecimal(valor); }
    private DepositoRequest deposito(String id, String valor) { return new DepositoRequest(id, monto(valor), "Depósito de prueba"); }
    private PagoRequest pago(String id, String valor) { return new PagoRequest(id, monto(valor), "Electricidad", "Pago de servicio"); }
    private TransferenciaRequest transferencia(String id, Long destino, String valor) { return new TransferenciaRequest(id, destino, monto(valor), "Transferencia de prueba"); }
    private BigDecimal saldo(Long id) { return jdbc.queryForObject("SELECT saldo FROM cuentas WHERE cuenta_id=?", BigDecimal.class, id); }
    private int contar(String tabla) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class); }
    private void saldos(String origen, String destino) {
        assertEquals(0, monto(origen).compareTo(saldo(101L)));
        assertEquals(0, monto(destino).compareTo(saldo(102L)));
    }

    @Test void depositoGuardaSaldoReciboYEventoEnLaMismaTransaccion() throws Exception {
        var op = service.deposito(101L, deposito("deposito-001", "1000"));
        saldos("6000", "8000");
        assertEquals("APROBADA", op.estado());
        assertEquals("DEPOSITO", op.tipo());
        assertNotNull(op.fecha());
        assertEquals(op, service.consultar(101L, "deposito-001"));
        assertThrows(OperacionNoEncontrada.class, () -> service.consultar(102L, "deposito-001"));
        assertEquals(1, contar("pagos_operaciones"));
        assertEquals(1, contar("pagos_outbox"));
        assertFalse(jdbc.queryForObject("SELECT publicado FROM pagos_outbox", Boolean.class));
        var evento = json.readValue(jdbc.queryForObject("SELECT payload FROM pagos_outbox", String.class), OperacionPagoResponse.class);
        assertEquals(op, evento);
    }

    @Test void pagoDescuentaSaldoYRegistraBeneficiario() {
        var op = service.pago(101L, pago("pago-servicio-01", "500"));
        saldos("4500", "8000");
        assertEquals("Electricidad", op.beneficiario());
        assertEquals("PAGO", op.tipo());
        assertNull(op.cuentaDestinoId());
        assertEquals(1, contar("pagos_outbox"));
    }

    @Test void transferenciaConfirmaDebitoYAbonoConservandoElTotal() {
        BigDecimal total = saldo(101L).add(saldo(102L));
        var op = service.transferencia(101L, transferencia("transferencia-01", 102L, "1000"));
        saldos("4000", "9000");
        assertEquals(0, total.compareTo(saldo(101L).add(saldo(102L))));
        assertEquals(0, monto("8000").compareTo(op.saldoDestinoAnterior()));
        assertEquals(0, monto("9000").compareTo(op.saldoDestinoPosterior()));
        assertEquals(1, contar("pagos_operaciones"));
        assertEquals(1, contar("pagos_outbox"));
    }

    @Test void repetirLosTresTiposDevuelveElMismoReciboSinDuplicarSaldosNiEventos() {
        var d = deposito("deposito-001", "500");
        assertEquals(service.deposito(101L, d), service.deposito(101L, d));
        var p = pago("pago-servicio-01", "100");
        assertEquals(service.pago(101L, p), service.pago(101L, p));
        var t = transferencia("transferencia-01", 102L, "200");
        assertEquals(service.transferencia(101L, t), service.transferencia(101L, t));
        saldos("5200", "8200");
        assertEquals(3, contar("pagos_operaciones"));
        assertEquals(3, contar("pagos_outbox"));
    }

    @Test void solicitudReutilizadaConOtroMontoOCuentaSeRechazaSinEfectos() {
        service.deposito(101L, deposito("deposito-001", "500"));
        assertThrows(OperacionConflicto.class, () -> service.deposito(101L, deposito("deposito-001", "501")));
        assertThrows(OperacionConflicto.class, () -> service.deposito(102L, deposito("deposito-001", "500")));
        saldos("5500", "8000");
        assertEquals(1, contar("pagos_operaciones"));
        assertEquals(1, contar("pagos_outbox"));
    }

    @Test void repetirUnReciboTrasElCierreNoReabreLaCuentaNiCobraDeNuevo() {
        var creado = service.deposito(103L, deposito("deposito-cierre-01", "100"));
        service.pago(103L, pago("pago-cierre-01", "100"));
        jdbc.update("INSERT INTO cuentas_estado(cuenta_id,estado,fecha_cierre) VALUES(103,'CERRADA',CURRENT_TIMESTAMP)");
        assertEquals(creado, service.deposito(103L, deposito("deposito-cierre-01", "100")));
        assertEquals(0, saldo(103L).signum());
        assertEquals("CERRADA", jdbc.queryForObject("SELECT estado FROM cuentas_estado WHERE cuenta_id=103", String.class));
        assertEquals(2, contar("pagos_operaciones"));
    }

    @Test void cuentasCerradasInexistentesYTransferenciaALaMismaCuentaNoProducenCambios() {
        assertThrows(OperacionConflicto.class, () -> service.deposito(104L, deposito("cerrada-deposito-01", "100")));
        assertThrows(OperacionConflicto.class, () -> service.pago(104L, pago("cerrada-pago-01", "100")));
        assertThrows(OperacionConflicto.class, () -> service.transferencia(101L, transferencia("cerrada-destino-01", 104L, "100")));
        assertThrows(OperacionNoEncontrada.class, () -> service.transferencia(101L, transferencia("destino-ausente-01", 999L, "100")));
        assertThrows(SolicitudInvalida.class, () -> service.transferencia(101L, transferencia("mismo-destino-01", 101L, "100")));
        saldos("5000", "8000");
        assertEquals(0, contar("pagos_operaciones"));
        assertEquals(0, contar("pagos_outbox"));
    }

    @Test void saldoInsuficienteNoProduceDebitosNiRegistrosParciales() {
        assertThrows(OperacionConflicto.class, () -> service.pago(101L, pago("sin-saldo-pago-01", "5001")));
        assertThrows(OperacionConflicto.class, () -> service.transferencia(101L, transferencia("sin-saldo-transfer-01", 102L, "5001")));
        saldos("5000", "8000");
        assertEquals(0, contar("pagos_operaciones"));
        assertEquals(0, contar("pagos_outbox"));
    }

    @Test void limiteDeSaldoYPrecisionNoPermitenDesbordesNiRedondeosSilenciosos() {
        jdbc.update("UPDATE cuentas SET saldo=9999999999999.98 WHERE cuenta_id=103");
        assertThrows(OperacionConflicto.class, () -> service.deposito(103L, deposito("saldo-limite-01", "0.02")));
        assertThrows(OperacionConflicto.class, () -> service.transferencia(101L, transferencia("saldo-destino-limite-01", 103L, "0.02")));
        assertThrows(SolicitudInvalida.class, () -> service.deposito(101L, deposito("precision-monto-01", "0.001")));
        saldos("5000", "8000");
        assertEquals(0, contar("pagos_operaciones"));
    }

    @Test void falloAlGuardarOutboxRevierteAmbosSaldosYElRegistro() {
        jdbc.execute("DROP TABLE pagos_outbox");
        assertThrows(DataAccessException.class, () -> service.transferencia(101L, transferencia("fallo-persistencia-01", 102L, "100")));
        saldos("5000", "8000");
        assertEquals(0, contar("pagos_operaciones"));
    }

    @Test void dosDepositosSimultaneosConLaMismaSolicitudSeAplicanUnaVez() throws Exception {
        var req = deposito("deposito-concurrente-01", "100");
        var resultados = competir(() -> service.deposito(101L, req), () -> service.deposito(101L, req));
        assertEquals(resultados.get(0), resultados.get(1));
        saldos("5100", "8000");
        assertEquals(1, contar("pagos_operaciones"));
        assertEquals(1, contar("pagos_outbox"));
    }

    @Test void pagosConcurrentesNoGastanElMismoSaldoDosVeces() throws Exception {
        var resultados = competir(() -> intentarPago("pago-concurrente-01"), () -> intentarPago("pago-concurrente-02"));
        assertNotEquals(resultados.get(0), resultados.get(1));
        saldos("2000", "8000");
        assertEquals(1, contar("pagos_operaciones"));
    }

    @Test void transferenciasOpuestasSimultaneasConservanElTotal() throws Exception {
        competir(() -> service.transferencia(101L, transferencia("opuesta-transfer-01", 102L, "100")),
                () -> service.transferencia(102L, transferencia("opuesta-transfer-02", 101L, "200")));
        saldos("5100", "7900");
        assertEquals(2, contar("pagos_operaciones"));
        assertEquals(2, contar("pagos_outbox"));
    }

    @Test void kafkaCaidoDejaEventoPendienteYElReintentoNoRepiteElCargo() {
        var op = service.pago(101L, pago("pago-kafka-pendiente-01", "100"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Broker caído")));
        publicador.publicarPendientes();
        assertFalse(jdbc.queryForObject("SELECT publicado FROM pagos_outbox", Boolean.class));
        assertEquals(1, jdbc.queryForObject("SELECT intentos FROM pagos_outbox", Integer.class));
        saldos("4900", "8000");
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        publicador.publicarPendientes();
        publicador.publicarPendientes();
        assertTrue(jdbc.queryForObject("SELECT publicado FROM pagos_outbox", Boolean.class));
        assertEquals(2, jdbc.queryForObject("SELECT intentos FROM pagos_outbox", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT fecha_publicacion FROM pagos_outbox", java.sql.Timestamp.class));
        assertEquals(op, service.pago(101L, pago("pago-kafka-pendiente-01", "100")));
        saldos("4900", "8000");
        assertEquals(1, contar("pagos_operaciones"));
        verify(kafka, times(2)).send(anyString(), anyString(), anyString());
    }

    private boolean intentarPago(String id) {
        try { service.pago(101L, pago(id, "3000")); return true; }
        catch (OperacionConflicto ex) { return false; }
    }

    @Test void mismaClaveEnDosCuentasSimultaneasSoloConfirmaUnaOperacion() throws Exception {
        var resultados = competir(() -> intentarDeposito(101L), () -> intentarDeposito(102L));
        assertNotEquals(resultados.get(0), resultados.get(1));
        assertEquals(0, monto("13100").compareTo(saldo(101L).add(saldo(102L))));
        assertEquals(1, contar("pagos_operaciones"));
        assertEquals(1, contar("pagos_outbox"));
        saldos(resultados.get(0) ? "5100" : "5000", resultados.get(1) ? "8100" : "8000");
    }

    private boolean intentarDeposito(Long cuenta) {
        try { service.deposito(cuenta, deposito("misma-clave-dos-cuentas-01", "100")); return true; }
        catch (OperacionConflicto ex) { return false; }
    }

    private <T> List<T> competir(Callable<T> primera, Callable<T> segunda) throws Exception {
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<T> a = pool.submit(() -> { if (!inicio.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return primera.call(); });
            Future<T> b = pool.submit(() -> { if (!inicio.await(5, TimeUnit.SECONDS)) throw new TimeoutException(); return segunda.call(); });
            inicio.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { inicio.countDown(); pool.shutdownNow(); }
    }
}
