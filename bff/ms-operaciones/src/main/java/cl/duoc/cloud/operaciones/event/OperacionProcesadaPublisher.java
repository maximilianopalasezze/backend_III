package cl.duoc.cloud.operaciones.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperacionProcesadaPublisher {

    private final KafkaTemplate<String, OperacionProcesadaEvent> kafkaTemplate;
    private final String topic;

    public OperacionProcesadaPublisher(
            KafkaTemplate<String, OperacionProcesadaEvent> kafkaTemplate,
            @Value("${app.kafka.topics.operaciones-procesadas}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publicar(OperacionProcesadaEvent evento) {
        kafkaTemplate.send(topic, evento.referencia(), evento);
    }
}
