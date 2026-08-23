package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;

public class ListenerReintentosBatch implements RetryListener {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerReintentosBatch.class);

    @Override
    public void beforeRetry(
            RetryPolicy politica,
            Retryable<?> operacion,
            RetryState estado) {

        if (estado.getRetryCount() > 0) {
            logger.warn(
                    "Reintento técnico {} ejecutado por el hilo {}. Causa anterior: {}",
                    estado.getRetryCount(),
                    Thread.currentThread().getName(),
                    obtenerMensaje(estado.getLastException())
            );
        }
    }

    @Override
    public void onRetryPolicyExhaustion(
            RetryPolicy politica,
            Retryable<?> operacion,
            RetryException excepcion) {

        Throwable ultimaExcepcion = excepcion.getLastException();

        if (ultimaExcepcion == null
                || !politica.shouldRetry(ultimaExcepcion)) {
            return;
        }

        logger.error(
                "Se agotó la política de reintentos después de {} intentos. Causa: {}",
                excepcion.getRetryCount(),
                obtenerMensaje(ultimaExcepcion)
        );
    }

    private String obtenerMensaje(Throwable excepcion) {
        if (excepcion == null) {
            return "Error no especificado";
        }

        if (excepcion.getMessage() == null
                || excepcion.getMessage().isBlank()) {
            return excepcion.getClass().getSimpleName();
        }

        return excepcion.getMessage();
    }
}
