package cl.duoc.cloud.pagos.service;

import cl.duoc.cloud.pagos.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.TimeUnit;

@Service
public class PublicacionOutboxService {
    private static final Logger log = LoggerFactory.getLogger(PublicacionOutboxService.class);
    private final OutboxRepository repo;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final long esperaMs;

    public PublicacionOutboxService(OutboxRepository repo, TransactionTemplate tx,
            KafkaTemplate<String, String> kafka, @Value("${app.outbox.espera-ms:7000}") long esperaMs) {
        this.repo = repo; this.tx = tx; this.kafka = kafka; this.esperaMs = esperaMs;
    }

    public void publicarPendientes() {
        for (String referencia : repo.pendientes()) {
            if (Thread.currentThread().isInterrupted()) return;
            tx.executeWithoutResult(status -> repo.bloquearPendiente(referencia).ifPresent(evento -> {
                try {
                    kafka.send(evento.topic(), evento.referencia(), evento.payload()).get(esperaMs, TimeUnit.MILLISECONDS);
                    repo.confirmar(referencia);
                    log.info("Evento de pago confirmado por Kafka | referencia={}", referencia);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    repo.fallar(referencia, "Publicación interrumpida");
                } catch (Exception ex) {
                    // Conservar el evento para el siguiente intento; el cobro ya está confirmado.
                    repo.fallar(referencia, ex.getClass().getSimpleName());
                    log.warn("Evento de pago pendiente | referencia={} | causa={}", referencia, ex.getClass().getSimpleName());
                }
            }));
        }
    }
}
