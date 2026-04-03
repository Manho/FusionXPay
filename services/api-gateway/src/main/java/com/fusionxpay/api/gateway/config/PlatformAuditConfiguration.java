package com.fusionxpay.api.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.api.gateway.audit.KafkaPlatformAuditPublisher;
import com.fusionxpay.api.gateway.audit.NoopPlatformAuditPublisher;
import com.fusionxpay.api.gateway.audit.PlatformAuditConnectPayloadFactory;
import com.fusionxpay.api.gateway.audit.PlatformAuditPublisher;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(PlatformAuditProperties.class)
public class PlatformAuditConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "platformAuditProducerFactory")
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    ProducerFactory<String, String> platformAuditProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1_000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2_000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 4_000);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformAuditKafkaTemplate")
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    KafkaTemplate<String, String> platformAuditKafkaTemplate(
            ProducerFactory<String, String> platformAuditProducerFactory) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(platformAuditProducerFactory);
        kafkaTemplate.setObservationEnabled(false);
        return kafkaTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    PlatformAuditConnectPayloadFactory platformAuditConnectPayloadFactory(ObjectMapper objectMapper) {
        return new PlatformAuditConnectPayloadFactory(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformAuditPublisher.class)
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    PlatformAuditPublisher kafkaPlatformAuditPublisher(KafkaTemplate<String, String> platformAuditKafkaTemplate,
                                                       PlatformAuditConnectPayloadFactory payloadFactory,
                                                       PlatformAuditProperties properties) {
        return new KafkaPlatformAuditPublisher(platformAuditKafkaTemplate, payloadFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformAuditPublisher.class)
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "false")
    PlatformAuditPublisher noopPlatformAuditPublisher() {
        return new NoopPlatformAuditPublisher();
    }
}
