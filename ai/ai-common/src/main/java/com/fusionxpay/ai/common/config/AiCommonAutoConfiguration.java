package com.fusionxpay.ai.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.audit.KafkaAuditEventPublisher;
import com.fusionxpay.ai.common.audit.NoopAuditEventPublisher;
import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.service.ConfirmationService;
import com.fusionxpay.ai.common.service.InMemoryConfirmationService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties({FusionXGatewayProperties.class, AuditProperties.class, ConfirmationProperties.class})
public class AiCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "fusionxGatewayRestClient")
    RestClient fusionxGatewayRestClient(FusionXGatewayProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayClient gatewayClient(RestClient fusionxGatewayRestClient, ObjectMapper objectMapper) {
        return new GatewayClient(fusionxGatewayRestClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "auditEventProducerFactory")
    @ConditionalOnProperty(prefix = "fusionx.ai.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    ProducerFactory<String, AuditEvent> auditEventProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Keep audit publishing from blocking MCP/CLI tool execution when Kafka metadata is unavailable.
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1_000);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2_000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 4_000);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "auditEventKafkaTemplate")
    @ConditionalOnProperty(prefix = "fusionx.ai.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate(ProducerFactory<String, AuditEvent> auditEventProducerFactory) {
        return new KafkaTemplate<>(auditEventProducerFactory);
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    @ConditionalOnProperty(prefix = "fusionx.ai.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    AuditEventPublisher kafkaAuditEventPublisher(KafkaTemplate<String, AuditEvent> auditEventKafkaTemplate,
                                                 AuditProperties auditProperties) {
        return new KafkaAuditEventPublisher(auditEventKafkaTemplate, auditProperties);
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventPublisher.class)
    @ConditionalOnProperty(prefix = "fusionx.ai.audit", name = "enabled", havingValue = "false")
    AuditEventPublisher noopAuditEventPublisher() {
        return new NoopAuditEventPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    ConfirmationService confirmationService(ConfirmationProperties confirmationProperties) {
        return new InMemoryConfirmationService(confirmationProperties);
    }
}
