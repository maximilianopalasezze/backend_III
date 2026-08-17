package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

public class ListenerResumenEstadosAnuales
        implements JobExecutionListener {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    ListenerResumenEstadosAnuales.class
            );

    private static final String NOMBRE_JOB =
            "jobEstadosCuentaAnuales";

    private final JdbcTemplate jdbcTemplate;
    private final String archivoOrigen;
    private final int anioProcesado;

    public ListenerResumenEstadosAnuales(
            JdbcTemplate jdbcTemplate,
            String archivoOrigen,
            int anioProcesado) {

        this.jdbcTemplate = jdbcTemplate;
        this.archivoOrigen = archivoOrigen;
        this.anioProcesado = anioProcesado;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {

        logger.info(
                "Iniciando generación de estados anuales. Archivo: {}, año: {}",
                archivoOrigen,
                anioProcesado
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
                DELETE FROM estados_cuenta_anuales
                WHERE anio = ?
                  AND archivo_origen = ?
                """,
                anioProcesado,
                archivoOrigen
        );

        jdbcTemplate.update("""
                DELETE FROM movimientos_anuales_procesados
                WHERE archivo_origen = ?
                """,
                archivoOrigen
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            generarEstadosCuenta();
        }

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

        Long movimientosPersistidos =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM movimientos_anuales_procesados
                        WHERE archivo_origen = ?
                        """,
                        Long.class,
                        archivoOrigen
                );

        Long estadosGenerados =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM estados_cuenta_anuales
                        WHERE anio = ?
                          AND archivo_origen = ?
                        """,
                        Long.class,
                        anioProcesado,
                        archivoOrigen
                );

        BigDecimal totalDepositos =
                obtenerTotal("total_depositos");

        BigDecimal totalRetiros =
                obtenerTotal("total_retiros");

        BigDecimal totalCompras =
                obtenerTotal("total_compras");

        BigDecimal totalPagos =
                obtenerTotal("total_pagos");

        BigDecimal saldoNetoAnual =
                obtenerTotal("saldo_anual");

        logger.info(
                "============================================"
        );

        logger.info(
                "RESUMEN DEL JOB DE ESTADOS DE CUENTA ANUALES"
        );

        logger.info(
                "Archivo procesado: {}",
                archivoOrigen
        );

        logger.info(
                "Año procesado: {}",
                anioProcesado
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
                "Movimientos persistidos: {}",
                movimientosPersistidos
        );

        logger.info(
                "Estados de cuenta generados: {}",
                estadosGenerados
        );

        logger.info(
                "Total depósitos: {}",
                totalDepositos
        );

        logger.info(
                "Total retiros: {}",
                totalRetiros
        );

        logger.info(
                "Total compras: {}",
                totalCompras
        );

        logger.info(
                "Total pagos: {}",
                totalPagos
        );

        logger.info(
                "Saldo neto anual: {}",
                saldoNetoAnual
        );

        logger.info(
                "Estado final: {}",
                jobExecution.getStatus()
        );

        logger.info(
                "============================================"
        );
    }

    private void generarEstadosCuenta() {

        int cantidadGenerada = jdbcTemplate.update("""
                INSERT INTO estados_cuenta_anuales (
                    cuenta_id,
                    anio,
                    cantidad_movimientos,
                    total_depositos,
                    total_retiros,
                    total_compras,
                    total_pagos,
                    saldo_anual,
                    archivo_origen
                )
                SELECT
                    cuenta_id,
                    ?,
                    COUNT(*),

                    COALESCE(SUM(
                        CASE
                            WHEN tipo_movimiento = 'deposito'
                            THEN ABS(monto)
                            ELSE 0
                        END
                    ), 0),

                    COALESCE(SUM(
                        CASE
                            WHEN tipo_movimiento = 'retiro'
                            THEN ABS(monto)
                            ELSE 0
                        END
                    ), 0),

                    COALESCE(SUM(
                        CASE
                            WHEN tipo_movimiento = 'compra'
                            THEN ABS(monto)
                            ELSE 0
                        END
                    ), 0),

                    COALESCE(SUM(
                        CASE
                            WHEN tipo_movimiento = 'pago'
                            THEN ABS(monto)
                            ELSE 0
                        END
                    ), 0),

                    COALESCE(SUM(monto), 0),
                    ?

                FROM movimientos_anuales_procesados

                WHERE archivo_origen = ?
                  AND YEAR(fecha) = ?

                GROUP BY cuenta_id
                """,
                anioProcesado,
                archivoOrigen,
                archivoOrigen,
                anioProcesado
        );

        logger.info(
                "Estados de cuenta anuales generados: {}",
                cantidadGenerada
        );
    }

    private BigDecimal obtenerTotal(String columna) {

        String sql = """
                SELECT COALESCE(SUM(%s), 0)
                FROM estados_cuenta_anuales
                WHERE anio = ?
                  AND archivo_origen = ?
                """.formatted(columna);

        return jdbcTemplate.queryForObject(
                sql,
                BigDecimal.class,
                anioProcesado,
                archivoOrigen
        );
    }
}