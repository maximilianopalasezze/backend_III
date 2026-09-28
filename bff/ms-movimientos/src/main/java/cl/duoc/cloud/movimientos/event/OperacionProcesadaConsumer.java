package cl.duoc.cloud.movimientos.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OperacionProcesadaConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(OperacionProcesadaConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.topics.operaciones-procesadas}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumir(OperacionProcesadaEvent evento) {

        log.info(
                "Evento Kafka recibido | referencia={} | cuentaId={} | tipo={} | monto={} | estado={}",
                evento.referencia(),
                evento.cuentaId(),
                evento.tipo(),
                evento.monto(),
                evento.estado()
        );

        if (evento.monto().compareTo(new BigDecimal("999000")) == 0) {
            log.error("Fallo controlado para demostrar reintentos y DLQ");
            throw new RuntimeException("Fallo controlado Semana 7");
        }
    }
}
