package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ListenerRendimientoBatch
        implements StepExecutionListener {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerRendimientoBatch.class);

    private final ThreadPoolTaskExecutor ejecutorBatch;
    private final JdbcTemplate jdbcTemplate;
    private final int cantidadHilos;
    private final int tamanoChunk;
    private final String idPrueba;

    private final ConcurrentMap<Long, Long> inicios =
            new ConcurrentHashMap<>();

    public ListenerRendimientoBatch(
            ThreadPoolTaskExecutor ejecutorBatch,
            JdbcTemplate jdbcTemplate,
            int cantidadHilos,
            int tamanoChunk,
            String idPrueba) {

        this.ejecutorBatch = ejecutorBatch;
        this.jdbcTemplate = jdbcTemplate;
        this.cantidadHilos = cantidadHilos;
        this.tamanoChunk = tamanoChunk;
        this.idPrueba = idPrueba;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        inicios.put(
                stepExecution.getId(),
                System.nanoTime()
        );

        logger.info(
                "Inicio del Step {}: prueba={}, chunk={}, hilos={}, prefijoHilo={}",
                stepExecution.getStepName(),
                idPrueba,
                tamanoChunk,
                cantidadHilos,
                ejecutorBatch.getThreadNamePrefix()
        );
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        Long inicio = inicios.remove(stepExecution.getId());

        long duracionNanos = inicio == null
                ? 0L
                : System.nanoTime() - inicio;

        long duracionMilisegundos =
                duracionNanos / 1_000_000;

        double duracionSegundos =
                duracionNanos / 1_000_000_000.0;

        double registrosPorSegundo =
                duracionSegundos <= 0
                        ? 0.0
                        : stepExecution.getReadCount()
                          / duracionSegundos;

        String nombreJob = stepExecution
                .getJobExecution()
                .getJobInstance()
                .getJobName();

        BigDecimal velocidad = BigDecimal
                .valueOf(registrosPorSegundo)
                .setScale(2, RoundingMode.HALF_UP);

        guardarMetricas(
                nombreJob,
                stepExecution,
                duracionMilisegundos,
                velocidad
        );

        logger.info("==============================================");
        logger.info(
                "RENDIMIENTO DEL STEP {}",
                stepExecution.getStepName()
        );
        logger.info("Identificador de prueba: {}", idPrueba);
        logger.info("Duración: {} ms", duracionMilisegundos);
        logger.info(
                "Velocidad promedio: {} registros/segundo",
                String.format(
                        Locale.ROOT,
                        "%.2f",
                        registrosPorSegundo
                )
        );
        logger.info(
                "Configuración evaluada: hilos={}, chunk={}",
                cantidadHilos,
                tamanoChunk
        );
        logger.info(
                "Chunks confirmados: {}",
                stepExecution.getCommitCount()
        );
        logger.info(
                "Lecturas: {}",
                stepExecution.getReadCount()
        );
        logger.info(
                "Escrituras: {}",
                stepExecution.getWriteCount()
        );
        logger.info(
                "Omisiones: {}",
                stepExecution.getSkipCount()
        );
        logger.info(
                "Commits: {}",
                stepExecution.getCommitCount()
        );
        logger.info(
                "Rollbacks: {}",
                stepExecution.getRollbackCount()
        );
        logger.info(
                "Pool configurado: tamaño={}, activos={}, cola={}",
                ejecutorBatch.getPoolSize(),
                ejecutorBatch.getActiveCount(),
                ejecutorBatch.getQueueSize()
        );
        logger.info(
                "Estado: {}",
                stepExecution.getStatus()
        );
        logger.info("==============================================");

        return stepExecution.getExitStatus();
    }

    private void guardarMetricas(
            String nombreJob,
            StepExecution stepExecution,
            long duracionMilisegundos,
            BigDecimal velocidad) {

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO metricas_rendimiento_batch (
                        nombre_job,
                        nombre_step,
                        id_prueba,
                        cantidad_hilos,
                        tamano_chunk,
                        duracion_ms,
                        registros_por_segundo,
                        registros_leidos,
                        registros_escritos,
                        registros_omitidos,
                        commits,
                        rollbacks,
                        estado
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    nombreJob,
                    stepExecution.getStepName(),
                    idPrueba,
                    cantidadHilos,
                    tamanoChunk,
                    duracionMilisegundos,
                    velocidad,
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount(),
                    stepExecution.getCommitCount(),
                    stepExecution.getRollbackCount(),
                    stepExecution.getStatus().name()
            );

            logger.info(
                    "Métricas de la prueba {} guardadas correctamente",
                    idPrueba
            );

        } catch (DataAccessException excepcion) {

            logger.error(
                    "No fue posible guardar las métricas de la prueba {}: {}",
                    idPrueba,
                    excepcion.getMessage()
            );
        }
    }
}