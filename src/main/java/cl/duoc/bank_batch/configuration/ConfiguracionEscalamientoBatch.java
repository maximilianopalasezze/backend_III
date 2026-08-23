package cl.duoc.bank_batch.configuration;

import cl.duoc.bank_batch.listener.ListenerHilosProcesamiento;
import cl.duoc.bank_batch.listener.ListenerReintentosBatch;
import cl.duoc.bank_batch.listener.ListenerRendimientoBatch;
import cl.duoc.bank_batch.politica.PoliticaOmisionDatosInvalidos;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

@Configuration
public class ConfiguracionEscalamientoBatch {

    @Bean(name = "ejecutorBatch")
    public ThreadPoolTaskExecutor ejecutorBatch(
            @Value("${batch.escalamiento.hilos:3}") int cantidadHilos,
            @Value("${batch.escalamiento.capacidad-cola:50}")
            int capacidadCola) {

        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(cantidadHilos);
        ejecutor.setMaxPoolSize(cantidadHilos);
        ejecutor.setQueueCapacity(capacidadCola);
        ejecutor.setThreadNamePrefix("batch-worker-");
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(60);
        ejecutor.setPrestartAllCoreThreads(true);
        return ejecutor;
    }

    @Bean(name = "politicaOmisionDatosInvalidos")
    public SkipPolicy politicaOmisionDatosInvalidos(
            @Value("${batch.tolerancia.limite-omisiones:2000}")
            long limiteOmisiones) {

        return new PoliticaOmisionDatosInvalidos(limiteOmisiones);
    }

    @Bean(name = "politicaReintentoTransitorio")
    public RetryPolicy politicaReintentoTransitorio(
            @Value("${batch.tolerancia.max-reintentos:3}")
            int maximoReintentos,
            @Value("${batch.tolerancia.pausa-reintento-ms:250}")
            long pausaMilisegundos) {

        return RetryPolicy.builder()
                .maxRetries(maximoReintentos)
                .includes(TransientDataAccessException.class)
                .delay(Duration.ofMillis(pausaMilisegundos))
                .build();
    }

    @Bean
    public ListenerReintentosBatch listenerReintentosBatch() {
        return new ListenerReintentosBatch();
    }

    @Bean
    public ListenerRendimientoBatch listenerRendimientoBatch(
            @Qualifier("ejecutorBatch")
            ThreadPoolTaskExecutor ejecutorBatch,
            @Value("${batch.escalamiento.hilos:3}") int cantidadHilos,
            @Value("${batch.escalamiento.chunk:5}") int tamanoChunk) {

        return new ListenerRendimientoBatch(
                ejecutorBatch,
                cantidadHilos,
                tamanoChunk
        );
    }

    @Bean
    public ListenerHilosProcesamiento listenerHilosProcesamiento() {
        return new ListenerHilosProcesamiento();
    }
}
