package cl.duoc.bank_batch;

import cl.duoc.bank_batch.servicio.ServicioEjecucionRecuperable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Pruebas con los tres Jobs reales y una base H2 exclusiva de los tests. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recuperacion;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.batch.job.enabled=false", "batch.reinicio.automatico=false",
        "batch.escalamiento.hilos=1", "batch.escalamiento.chunk=50",
        "batch.prueba.fallar-despues-commits=2",
        "logging.level.root=ERROR", "logging.level.cl.duoc.bank_batch=OFF",
        "logging.file.name=", "spring.main.banner-mode=off",
        "batch.rendimiento.id-prueba=test-recuperacion",
        "batch.archivo.transacciones=data/semana_9/movimientos_financieros_diarios.csv",
        "batch.archivo.intereses=data/semana_9/intereses_trimestrales.csv",
        "batch.archivo.estados=data/semana_9/estados_financieros_anuales.csv"
})
@SuppressWarnings("removal")
class RecuperacionBatchTests {
    @Autowired Map<String, Job> jobs;
    @Autowired JobOperator operador;
    @Autowired JobRepository repositorio;
    @Autowired JdbcTemplate jdbc;
    @Autowired ServicioEjecucionRecuperable servicio;

    @ParameterizedTest
    @ValueSource(strings = {"jobTransaccionesDiarias", "jobInteresesMensuales", "jobEstadosCuentaAnuales"})
    void recuperaLosTresJobsDesdeCheckpointConElMismoResultado(String nombreJob) throws Exception {
        Job job = jobs.get(nombreJob);
        assertEquals(BatchStatus.COMPLETED, operador.run(job, parametros(false)).getStatus());
        List<Map<String, Object>> esperado = resultados(nombreJob);
        List<Map<String, Object>> rechazosEsperados = rechazos(nombreJob);
        List<Map<String, Object>> detallesEsperados = detalles(nombreJob);
        assertFalse(esperado.isEmpty());
        assertFalse(rechazosEsperados.isEmpty());

        JobParameters parametros = parametros(true);
        JobExecution recuperada = servicio.ejecutar(job, parametros, 3);
        assertEquals(BatchStatus.COMPLETED, recuperada.getStatus());
        List<JobExecution> ejecuciones = repositorio.getJobExecutions(recuperada.getJobInstance()).stream()
                .sorted(Comparator.comparing(JobExecution::getId)).toList();
        assertEquals(2, ejecuciones.size());
        assertEquals(BatchStatus.FAILED, ejecuciones.get(0).getStatus());
        assertEquals(BatchStatus.COMPLETED, ejecuciones.get(1).getStatus());
        var fallida = repositorio.getJobExecution(ejecuciones.get(0).getId()).getStepExecutions().iterator().next();
        var reanudada = repositorio.getJobExecution(ejecuciones.get(1).getId()).getStepExecutions().iterator().next();
        assertEquals(100, fallida.getReadCount());
        assertEquals(900, reanudada.getReadCount());
        assertEquals(esperado, resultados(nombreJob));
        assertEquals(rechazosEsperados, rechazos(nombreJob));
        assertEquals(detallesEsperados, detalles(nombreJob));

        // Invocar de nuevo un lote completo conserva sus resultados y no crea otra ejecución.
        assertEquals(recuperada.getId(), servicio.ejecutar(job, parametros, 3).getId());
        assertEquals(2, repositorio.getJobExecutions(recuperada.getJobInstance()).size());
    }

    @Test
    void respetaElLimiteYPuedeRetomarEnOtraInvocacionDelServicio() throws Exception {
        Job job = jobs.get("jobTransaccionesDiarias");
        JobParameters parametros = parametros(true);
        assertThrows(IllegalStateException.class, () -> servicio.ejecutar(job, parametros, 1));
        JobExecution fallida = repositorio.getLastJobExecution(job.getName(), parametros);
        assertEquals(BatchStatus.FAILED, fallida.getStatus());
        JobExecution completa = servicio.ejecutar(job, parametros, 3);
        assertEquals(fallida.getJobInstance().getId(), completa.getJobInstance().getId());
        assertEquals(BatchStatus.COMPLETED, completa.getStatus());
        assertEquals(480, resultados(job.getName()).size());
        assertEquals(520, rechazos(job.getName()).size());
    }

    @Test
    void rechazaCambiarElArchivoDeUnLoteConCheckpoint() throws Exception {
        Job job = jobs.get("jobTransaccionesDiarias");
        JobParameters originales = new JobParametersBuilder(parametros(true))
                .addString("archivo", "original.csv", false).toJobParameters();
        assertThrows(IllegalStateException.class, () -> servicio.ejecutar(job, originales, 1));
        JobExecution fallida = repositorio.getLastJobExecution(job.getName(), originales);
        JobParameters alterados = new JobParametersBuilder(originales)
                .addString("archivo", "otro.csv", false).toJobParameters();
        assertThrows(IllegalArgumentException.class, () -> servicio.ejecutar(job, alterados, 3));
        assertEquals(1, repositorio.getJobExecutions(fallida.getJobInstance()).size());
    }

    private JobParameters parametros(boolean fallo) {
        return new JobParametersBuilder().addString("lote", UUID.randomUUID().toString())
                .addString("simular.fallo", Boolean.toString(fallo), false).toJobParameters();
    }

    private List<Map<String, Object>> resultados(String job) {
        String sql = switch (job) {
            case "jobTransaccionesDiarias" -> "SELECT transaccion_id, fecha, monto, tipo, es_anomalia, detalle_anomalia FROM transacciones_procesadas ORDER BY transaccion_id";
            case "jobInteresesMensuales" -> "SELECT cuenta_id, periodo, saldo_inicial, tasa_interes, interes_calculado, saldo_final FROM intereses_calculados ORDER BY cuenta_id";
            case "jobEstadosCuentaAnuales" -> "SELECT cuenta_id, anio, cantidad_movimientos, total_depositos, total_retiros, total_compras, total_pagos, saldo_anual FROM estados_cuenta_anuales ORDER BY cuenta_id";
            default -> throw new IllegalArgumentException(job);
        };
        return jdbc.queryForList(sql);
    }

    private List<Map<String, Object>> detalles(String job) {
        return switch (job) {
            case "jobTransaccionesDiarias" -> jdbc.queryForList("SELECT fecha, cantidad_transacciones, cantidad_debitos, cantidad_creditos, monto_total_debitos, monto_total_creditos, cantidad_anomalias FROM resumen_transacciones_diarias ORDER BY fecha");
            case "jobInteresesMensuales" -> jdbc.queryForList("SELECT cuenta_id, nombre, saldo, edad, tipo_cuenta FROM cuentas ORDER BY cuenta_id");
            case "jobEstadosCuentaAnuales" -> jdbc.queryForList("SELECT cuenta_id, fecha, tipo_movimiento, monto, descripcion FROM movimientos_anuales_procesados ORDER BY cuenta_id, fecha, tipo_movimiento, monto, descripcion");
            default -> throw new IllegalArgumentException(job);
        };
    }

    private List<Map<String, Object>> rechazos(String job) {
        return jdbc.queryForList("SELECT numero_linea, contenido_original, motivo_rechazo FROM registros_rechazados WHERE nombre_job = ? ORDER BY numero_linea, contenido_original", job);
    }
}
