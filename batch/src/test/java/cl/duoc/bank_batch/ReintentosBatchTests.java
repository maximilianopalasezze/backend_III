package cl.duoc.bank_batch;

import cl.duoc.bank_batch.listener.ListenerReintentosBatch;
import cl.duoc.bank_batch.modelo.TransaccionProcesada;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.retry.RetryState;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Job real, CSV oficial y H2 aislada; inyecta un error JDBC antes de escribir. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reintentos_s9;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.batch.job.enabled=false", "batch.reinicio.automatico=false",
        "batch.escalamiento.hilos=1", "batch.escalamiento.chunk=50",
        "batch.prueba.fallar-despues-commits=0",
        "batch.tolerancia.max-reintentos=3", "batch.tolerancia.pausa-reintento-ms=1",
        "logging.level.root=ERROR", "logging.level.cl.duoc.bank_batch=OFF",
        "logging.file.name=", "spring.main.banner-mode=off",
        "batch.rendimiento.id-prueba=test-reintentos-jdbc",
        "batch.archivo.transacciones=data/semana_9/movimientos_financieros_diarios.csv"
})
@SuppressWarnings("removal")
class ReintentosBatchTests {
    @Autowired @Qualifier("jobTransaccionesDiarias") Job job;
    @Autowired JobOperator operador;
    @Autowired JobRepository repositorio;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean(name = "escritorTransacciones")
    JdbcBatchItemWriter<TransaccionProcesada> escritor;
    @MockitoSpyBean(name = "listenerReintentosBatch")
    ListenerReintentosBatch listener;

    @ParameterizedTest
    @ValueSource(strings = {"transitoria", "recuperable", "conexion"})
    void reintentaElFalloJdbcYConservaLosResultados(String tipo) throws Exception {
        assertEquals(BatchStatus.COMPLETED, ejecutarJob().getStatus());
        List<Map<String, Object>> esperado = resultados();
        assertEquals(480, esperado.size());

        AtomicBoolean pendiente = new AtomicBoolean(true);
        AtomicReference<DataAccessException> traducida = new AtomicReference<>();
        AtomicInteger reintentos = contarReintentos();
        SQLException fallo = switch (tipo) {
            case "transitoria" -> new SQLTransientConnectionException("Conexion temporal simulada", "08006");
            case "recuperable" -> new SQLRecoverableException("Conexion recuperable simulada", "08006");
            default -> new SQLException("Recurso de conexion simulado", "08006");
        };
        doAnswer(invocacion -> {
            if (pendiente.compareAndSet(true, false)) {
                try {
                    jdbc.execute((ConnectionCallback<Void>) conexion -> { throw fallo; });
                } catch (DataAccessException error) {
                    traducida.set(error);
                    throw error;
                }
            }
            return invocacion.callRealMethod();
        }).when(escritor).write(any());

        JobExecution ejecucion = ejecutarJob();
        assertNotNull(traducida.get(), "El fallo JDBC debe pasar por la traduccion de Spring");
        assertTrue(reintentos.get() > 0, "Debe observarse Retry, no solo otra escritura");
        assertEquals(BatchStatus.COMPLETED, ejecucion.getStatus());
        assertEquals(1, repositorio.getJobExecutions(ejecucion.getJobInstance()).size());
        var step = ejecucion.getStepExecutions().iterator().next();
        assertEquals(1000, step.getReadCount());
        assertEquals(480, step.getWriteCount());
        assertEquals(520, step.getSkipCount());
        assertEquals(esperado, resultados());
        assertEquals(480L, jdbc.queryForObject("SELECT COUNT(DISTINCT transaccion_id) FROM transacciones_procesadas", Long.class));
        assertEquals(520L, jdbc.queryForObject("SELECT COUNT(*) FROM registros_rechazados WHERE nombre_job = 'jobTransaccionesDiarias'", Long.class));
        System.out.printf("REINTENTO BATCH OK: falla=%s | traduccion=%s | reintentos=%d | COMPLETED | leidos=1000 escritos=480 rechazados=520 | resultados identicos%n",
                tipo, traducida.get().getClass().getSimpleName(), reintentos.get());
    }

    @Test
    void elSqlInvalidoFallaSinReintentos() throws Exception {
        AtomicInteger reintentos = contarReintentos();
        doThrow(new BadSqlGrammarException("prueba", "SQL invalido",
                new SQLException("Sintaxis invalida simulada", "42000")))
                .when(escritor).write(any());
        JobExecution ejecucion = ejecutarJob();
        assertEquals(BatchStatus.FAILED, ejecucion.getStatus());
        assertEquals(0, reintentos.get());
        assertEquals(1, repositorio.getJobExecutions(ejecucion.getJobInstance()).size());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM transacciones_procesadas", Long.class));
        System.out.println("ERROR PERMANENTE OK: SQL invalido -> FAILED | reintentos=0 | escrituras=0");
    }

    private AtomicInteger contarReintentos() {
        AtomicInteger cantidad = new AtomicInteger();
        doAnswer(invocacion -> {
            RetryState estado = invocacion.getArgument(2);
            if (estado.getRetryCount() > 0) cantidad.incrementAndGet();
            return invocacion.callRealMethod();
        }).when(listener).beforeRetry(any(), any(), any());
        return cantidad;
    }

    private JobExecution ejecutarJob() throws Exception {
        return operador.run(job, new JobParametersBuilder()
                .addString("lote", "s9-retry-" + UUID.randomUUID()).toJobParameters());
    }

    private List<Map<String, Object>> resultados() {
        return jdbc.queryForList("SELECT transaccion_id, fecha, monto, tipo, es_anomalia, detalle_anomalia FROM transacciones_procesadas ORDER BY transaccion_id");
    }
}
