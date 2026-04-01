package com.fusionxpay.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableConfigurationProperties({KafkaProperties.class, AiAuditConsumerProperties.class})
@ConditionalOnProperty(prefix = "fusionx.ai.audit", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class AiAuditKafkaConfig {

    @Bean
    ConsumerFactory<String, AuditEvent> auditEventConsumerFactory(KafkaProperties kafkaProperties,
                                                                  ObjectMapper objectMapper,
                                                                  AiAuditConsumerProperties auditProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, auditProperties.getConsumerGroup());
        properties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<AuditEvent> valueDeserializer = new JsonDeserializer<>(AuditEvent.class, objectMapper, false);
        valueDeserializer.addTrustedPackages("com.fusionxpay.ai.common.audit");
        valueDeserializer.ignoreTypeHeaders();

        return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, AuditEvent> auditEventKafkaListenerContainerFactory(
            ConsumerFactory<String, AuditEvent> auditEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, AuditEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditEventConsumerFactory);
        factory.setCommonErrorHandler(new CommonLoggingErrorHandler());
        factory.setConcurrency(1);
        factory.getContainerProperties().setObservationEnabled(false);
        return factory;
    }
}
