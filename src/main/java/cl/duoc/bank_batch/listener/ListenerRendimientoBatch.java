package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ListenerRendimientoBatch
        implements StepExecutionListener {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerRendimientoBatch.class);

    private final ThreadPoolTaskExecutor ejecutorBatch;
    private final int cantidadHilos;
    private final int tamanoChunk;

    private final ConcurrentMap<Long, Long> inicios =
            new ConcurrentHashMap<>();

    public ListenerRendimientoBatch(
            ThreadPoolTaskExecutor ejecutorBatch,
            int cantidadHilos,
            int tamanoChunk) {

        this.ejecutorBatch = ejecutorBatch;
        this.cantidadHilos = cantidadHilos;
        this.tamanoChunk = tamanoChunk;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        inicios.put(stepExecution.getId(), System.nanoTime());

        logger.info(
                "Inicio del Step {}: chunk={}, hilos={}, prefijoHilo={}",
                stepExecution.getStepName(),
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

        double duracionSegundos = duracionNanos / 1_000_000_000.0;
        double registrosPorSegundo = duracionSegundos <= 0
                ? 0.0
                : stepExecution.getReadCount() / duracionSegundos;

        logger.info("==============================================");
        logger.info("RENDIMIENTO DEL STEP {}", stepExecution.getStepName());
        logger.info("Duración: {} ms", duracionNanos / 1_000_000);
        logger.info(
                "Velocidad promedio: {} registros/segundo",
                String.format("%.2f", registrosPorSegundo)
        );
        logger.info("Chunks confirmados: {}", stepExecution.getCommitCount());
        logger.info("Lecturas: {}", stepExecution.getReadCount());
        logger.info("Escrituras: {}", stepExecution.getWriteCount());
        logger.info("Omisiones: {}", stepExecution.getSkipCount());
        logger.info("Commits: {}", stepExecution.getCommitCount());
        logger.info("Rollbacks: {}", stepExecution.getRollbackCount());
        logger.info(
                "Pool configurado: tamaño={}, activos={}, cola={}",
                ejecutorBatch.getPoolSize(),
                ejecutorBatch.getActiveCount(),
                ejecutorBatch.getQueueSize()
        );
        logger.info("Estado: {}", stepExecution.getStatus());
        logger.info("==============================================");

        return stepExecution.getExitStatus();
    }
}
