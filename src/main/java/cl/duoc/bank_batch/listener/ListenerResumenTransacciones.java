package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;

public class ListenerResumenTransacciones
        implements JobExecutionListener {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerResumenTransacciones.class);

    private static final String NOMBRE_JOB =
            "jobTransaccionesDiarias";

    private final JdbcTemplate jdbcTemplate;
    private final String archivoOrigen;

    public ListenerResumenTransacciones(
            JdbcTemplate jdbcTemplate,
            String archivoOrigen
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.archivoOrigen = archivoOrigen;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {

        jdbcTemplate.update(
                """
                DELETE FROM registros_rechazados
                WHERE nombre_job = ?
                  AND archivo_origen = ?
                """,
                NOMBRE_JOB,
                archivoOrigen
        );

        jdbcTemplate.update(
                """
                DELETE FROM resumen_transacciones_diarias
                WHERE archivo_origen = ?
                """,
                archivoOrigen
        );

        logger.info(
                "Iniciando procesamiento del archivo: {}",
                archivoOrigen
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            logger.error(
                    "El Job finalizó con estado: {}",
                    jobExecution.getStatus()
            );
            return;
        }

        generarResumenDiario();

        long procesadas = contarRegistros(
                """
                SELECT COUNT(*)
                FROM transacciones_procesadas
                WHERE archivo_origen = ?
                """,
                archivoOrigen
        );

        long rechazadas = contarRegistros(
                """
                SELECT COUNT(*)
                FROM registros_rechazados
                WHERE nombre_job = ?
                  AND archivo_origen = ?
                """,
                NOMBRE_JOB,
                archivoOrigen
        );

        long anomalias = contarRegistros(
                """
                SELECT COUNT(*)
                FROM transacciones_procesadas
                WHERE archivo_origen = ?
                  AND es_anomalia = TRUE
                """,
                archivoOrigen
        );

        logger.info("==============================================");
        logger.info("RESUMEN DEL JOB DE TRANSACCIONES DIARIAS");
        logger.info("Archivo procesado: {}", archivoOrigen);
        logger.info("Registros procesados: {}", procesadas);
        logger.info("Registros rechazados: {}", rechazadas);
        logger.info("Anomalías detectadas: {}", anomalias);
        logger.info("Estado final: {}", jobExecution.getStatus());
        logger.info("==============================================");
    }

    private void generarResumenDiario() {

        String sql = """
                INSERT INTO resumen_transacciones_diarias (
                    fecha,
                    cantidad_transacciones,
                    cantidad_debitos,
                    cantidad_creditos,
                    monto_total_debitos,
                    monto_total_creditos,
                    cantidad_anomalias,
                    archivo_origen
                )
                SELECT
                    fecha,
                    COUNT(*),
                    SUM(CASE
                        WHEN tipo = 'debito' THEN 1
                        ELSE 0
                    END),
                    SUM(CASE
                        WHEN tipo = 'credito' THEN 1
                        ELSE 0
                    END),
                    COALESCE(SUM(CASE
                        WHEN tipo = 'debito' THEN ABS(monto)
                        ELSE 0
                    END), 0),
                    COALESCE(SUM(CASE
                        WHEN tipo = 'credito' THEN ABS(monto)
                        ELSE 0
                    END), 0),
                    SUM(CASE
                        WHEN es_anomalia = TRUE THEN 1
                        ELSE 0
                    END),
                    ?
                FROM transacciones_procesadas
                WHERE archivo_origen = ?
                GROUP BY fecha
                """;

        jdbcTemplate.update(
                sql,
                archivoOrigen,
                archivoOrigen
        );
    }

    private long contarRegistros(
            String sql,
            Object... parametros
    ) {
        Long cantidad = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                parametros
        );

        return cantidad == null ? 0 : cantidad;
    }
}