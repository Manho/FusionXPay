package com.fusionxpay.api.gateway.config;

import com.fusionxpay.api.gateway.audit.KafkaPlatformAuditPublisher;
import com.fusionxpay.api.gateway.audit.NoopPlatformAuditPublisher;
import com.fusionxpay.api.gateway.audit.PlatformAuditPublisher;
import com.fusionxpay.common.audit.PlatformAuditEvent;
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
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(PlatformAuditProperties.class)
public class PlatformAuditConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "platformAuditProducerFactory")
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    ProducerFactory<String, PlatformAuditEvent> platformAuditProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1_000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2_000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 4_000);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformAuditKafkaTemplate")
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    KafkaTemplate<String, PlatformAuditEvent> platformAuditKafkaTemplate(
            ProducerFactory<String, PlatformAuditEvent> platformAuditProducerFactory) {
        KafkaTemplate<String, PlatformAuditEvent> kafkaTemplate = new KafkaTemplate<>(platformAuditProducerFactory);
        kafkaTemplate.setObservationEnabled(false);
        return kafkaTemplate;
    }

    @Bean
    @ConditionalOnMissingBean(PlatformAuditPublisher.class)
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    PlatformAuditPublisher kafkaPlatformAuditPublisher(KafkaTemplate<String, PlatformAuditEvent> platformAuditKafkaTemplate,
                                                       PlatformAuditProperties properties) {
        return new KafkaPlatformAuditPublisher(platformAuditKafkaTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformAuditPublisher.class)
    @ConditionalOnProperty(prefix = "fusionx.platform.audit", name = "enabled", havingValue = "false")
    PlatformAuditPublisher noopPlatformAuditPublisher() {
        return new NoopPlatformAuditPublisher();
    }
}
