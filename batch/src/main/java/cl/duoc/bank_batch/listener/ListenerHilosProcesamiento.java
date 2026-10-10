package cl.duoc.bank_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.ItemProcessListener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Deja evidencia de los hilos que procesan los registros sin generar
 * una línea de log por cada elemento del archivo.
 */
public class ListenerHilosProcesamiento
        implements ItemProcessListener<Object, Object> {

    private static final Logger logger =
            LoggerFactory.getLogger(ListenerHilosProcesamiento.class);

    private final AtomicLong elementosProcesados = new AtomicLong();

    @Override
    public void afterProcess(Object item, Object resultado) {
        long numeroElemento = elementosProcesados.incrementAndGet();

        if (numeroElemento <= 9 || numeroElemento % 200 == 0) {
            logger.info(
                    "Elemento {} procesado por el hilo {}",
                    numeroElemento,
                    Thread.currentThread().getName()
            );
        }
    }
}
