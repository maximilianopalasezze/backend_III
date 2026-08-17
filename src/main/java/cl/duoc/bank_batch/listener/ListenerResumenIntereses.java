package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

public class ListenerResumenIntereses
        implements JobExecutionListener {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerResumenIntereses.class);

    private static final String NOMBRE_JOB =
            "jobInteresesMensuales";

    private final JdbcTemplate jdbcTemplate;
    private final String archivoOrigen;
    private final String periodo;

    public ListenerResumenIntereses(
            JdbcTemplate jdbcTemplate,
            String archivoOrigen,
            String periodo) {

        this.jdbcTemplate = jdbcTemplate;
        this.archivoOrigen = archivoOrigen;
        this.periodo = periodo;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {

        logger.info(
                "Iniciando cálculo de intereses. Archivo: {}, periodo: {}",
                archivoOrigen,
                periodo
        );

        jdbcTemplate.update("""
                DELETE FROM registros_rechazados
                WHERE nombre_job = ?
                  AND archivo_origen = ?
                """,
                NOMBRE_JOB,
                archivoOrigen
        );

        jdbcTemplate.update("""
                DELETE FROM intereses_calculados
                WHERE periodo = ?
                  AND archivo_origen = ?
                """,
                periodo,
                archivoOrigen
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        long registrosLeidos = 0;
        long registrosProcesados = 0;
        long registrosRechazados = 0;

        for (StepExecution stepExecution
                : jobExecution.getStepExecutions()) {

            registrosLeidos += stepExecution.getReadCount();
            registrosProcesados += stepExecution.getWriteCount();

            registrosRechazados +=
                    stepExecution.getReadSkipCount()
                            + stepExecution.getProcessSkipCount()
                            + stepExecution.getWriteSkipCount();
        }

        Long cantidadPersistida = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM intereses_calculados
                WHERE periodo = ?
                  AND archivo_origen = ?
                """,
                Long.class,
                periodo,
                archivoOrigen
        );

        BigDecimal totalIntereses = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(interes_calculado), 0)
                FROM intereses_calculados
                WHERE periodo = ?
                  AND archivo_origen = ?
                """,
                BigDecimal.class,
                periodo,
                archivoOrigen
        );

        BigDecimal totalSaldosFinales = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(saldo_final), 0)
                FROM intereses_calculados
                WHERE periodo = ?
                  AND archivo_origen = ?
                """,
                BigDecimal.class,
                periodo,
                archivoOrigen
        );

        logger.info(
                "============================================"
        );

        logger.info(
                "RESUMEN DEL JOB DE INTERESES MENSUALES"
        );

        logger.info(
                "Archivo procesado: {}",
                archivoOrigen
        );

        logger.info(
                "Periodo calculado: {}",
                periodo
        );

        logger.info(
                "Registros leídos: {}",
                registrosLeidos
        );

        logger.info(
                "Registros procesados: {}",
                registrosProcesados
        );

        logger.info(
                "Registros rechazados: {}",
                registrosRechazados
        );

        logger.info(
                "Registros persistidos: {}",
                cantidadPersistida
        );

        logger.info(
                "Total de intereses calculados: {}",
                totalIntereses
        );

        logger.info(
                "Total de saldos finales: {}",
                totalSaldosFinales
        );

        logger.info(
                "Estado final: {}",
                jobExecution.getStatus()
        );

        logger.info(
                "============================================"
        );
    }
}