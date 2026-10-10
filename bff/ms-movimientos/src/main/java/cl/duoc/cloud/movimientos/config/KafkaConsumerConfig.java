package cl.duoc.cloud.movimientos.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import cl.duoc.cloud.movimientos.event.PagoProcesadoEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import java.util.HashMap;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, ex) ->
                                new TopicPartition(
                                        "operaciones.fallidas",
                                        record.partition()
                                )
                );

        // 2 reintentos, esperando 2 segundos entre cada uno.
        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(2000L, 2L)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PagoProcesadoEvent>
    pagosKafkaListenerContainerFactory(ConsumerFactory<Object, Object> consumerFactory,
                                       DefaultErrorHandler kafkaErrorHandler) {
        var propiedades = new HashMap<String, Object>(consumerFactory.getConfigurationProperties());
        propiedades.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        propiedades.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        propiedades.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PagoProcesadoEvent.class.getName());
        propiedades.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        propiedades.put(JsonDeserializer.TRUSTED_PACKAGES, "cl.duoc.cloud.movimientos.event");
        propiedades.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, PagoProcesadoEvent>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(propiedades));
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
