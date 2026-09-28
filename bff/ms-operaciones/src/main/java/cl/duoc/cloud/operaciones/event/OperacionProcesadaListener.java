package cl.duoc.cloud.operaciones.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OperacionProcesadaListener {

    private final OperacionProcesadaPublisher publisher;

    public OperacionProcesadaListener(OperacionProcesadaPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void procesar(OperacionProcesadaEvent evento) {
        publisher.publicar(evento);
    }
}
