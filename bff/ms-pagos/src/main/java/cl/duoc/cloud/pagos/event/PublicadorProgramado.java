package cl.duoc.cloud.pagos.event;

import cl.duoc.cloud.pagos.service.PublicacionOutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class PublicadorProgramado {
    private final PublicacionOutboxService service;
    public PublicadorProgramado(PublicacionOutboxService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:2000}")
    public void publicar() { service.publicarPendientes(); }
}
