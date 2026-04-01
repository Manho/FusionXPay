package com.fusionxpay.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableConfigurationProperties({KafkaProperties.class, AiAuditConsumerProperties.class})
@ConditionalOnProperty(prefix = "fusionx.ai.audit.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAuditKafkaConfig {

    @Bean
    ConsumerFactory<String, AuditEvent> auditEventConsumerFactory(KafkaProperties kafkaProperties,
                                                                  ObjectMapper objectMapper,
                                                                  AiAuditConsumerProperties auditProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, auditProperties.getGroup());
        properties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<AuditEvent> valueDeserializer = new JsonDeserializer<>(AuditEvent.class, objectMapper, false);
        valueDeserializer.addTrustedPackages("com.fusionxpay.ai.common.audit");
        valueDeserializer.ignoreTypeHeaders();

        return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ProducerFactory<String, AuditEvent> auditEventDltProducerFactory(KafkaProperties kafkaProperties,
                                                                     ObjectMapper objectMapper) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), new JsonSerializer<>(objectMapper));
    }

    @Bean
    KafkaTemplate<String, AuditEvent> auditEventDltKafkaTemplate(ProducerFactory<String, AuditEvent> auditEventDltProducerFactory) {
        return new KafkaTemplate<>(auditEventDltProducerFactory);
    }

    @Bean
    DeadLetterPublishingRecoverer aiAuditDeadLetterPublishingRecoverer(
            KafkaOperations<String, AuditEvent> auditEventDltKafkaTemplate,
            AiAuditConsumerProperties auditProperties) {
        return new DeadLetterPublishingRecoverer(
                auditEventDltKafkaTemplate,
                (record, ex) -> new TopicPartition(auditProperties.getDltTopic(), record.partition())
        );
    }

    @Bean
    DefaultErrorHandler aiAuditKafkaErrorHandler(DeadLetterPublishingRecoverer aiAuditDeadLetterPublishingRecoverer) {
        return new DefaultErrorHandler(aiAuditDeadLetterPublishingRecoverer, new FixedBackOff(1000L, 3L));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AuditEvent> auditEventKafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEvent> auditEventConsumerFactory,
            DefaultErrorHandler aiAuditKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditEventConsumerFactory);
        factory.setCommonErrorHandler(aiAuditKafkaErrorHandler);
        factory.setConcurrency(1);
        factory.getContainerProperties().setObservationEnabled(false);
        return factory;
    }
}
