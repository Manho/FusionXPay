package com.fusionxpay.admin.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditSource;
import com.fusionxpay.ai.common.audit.AuditStatus;
import com.fusionxpay.admin.model.AiAuditLog;
import com.fusionxpay.admin.repository.AiAuditLogRepository;
import com.fusionxpay.common.test.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "fusionx.ai.audit.enabled=false",
        "fusionx.ai.audit.topic=ai-audit-log",
        "fusionx.ai.audit.consumer-enabled=true",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Import(AiAuditKafkaIT.KafkaProducerTestConfig.class)
class AiAuditKafkaIT extends AbstractIntegrationTest {

    private static final String GROUP_ID = "admin-audit-it-" + UUID.randomUUID();

    @Autowired
    private KafkaTemplate<String, AuditEvent> kafkaTemplate;

    @Autowired
    private AiAuditLogRepository repository;

    @DynamicPropertySource
    static void auditConsumerProperties(DynamicPropertyRegistry registry) {
        registry.add("fusionx.ai.audit.consumer-group", () -> GROUP_ID);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void persistsCliAuditEventIntoMysql() {
        AuditEvent event = sampleEvent();

        kafkaTemplate.send("ai-audit-log", event.getCorrelationId(), event).join();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    AiAuditLog persisted = repository.findByEventId(event.getEventId()).orElseThrow();
                    assertThat(persisted.getSource()).isEqualTo(AuditSource.CLI);
                    assertThat(persisted.getMerchantId()).isEqualTo(101L);
                    assertThat(persisted.getActionName()).isEqualTo("payment.query");
                    assertThat(persisted.getStatus()).isEqualTo(AuditStatus.SUCCESS);
                });
    }

    @Test
    void ignoresDuplicateEventsByEventId() {
        AuditEvent event = sampleEvent();

        kafkaTemplate.send("ai-audit-log", event.getCorrelationId(), event).join();
        kafkaTemplate.send("ai-audit-log", event.getCorrelationId(), event).join();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(repository.count()).isEqualTo(1));
    }

    private AuditEvent sampleEvent() {
        return AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .source(AuditSource.CLI)
                .merchantId(101L)
                .actionName("payment.query")
                .status(AuditStatus.SUCCESS)
                .durationMs(123L)
                .inputSummary("transactionId=txn_123")
                .outputSummary("status=PROCESSING")
                .timestamp(Instant.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    @TestConfiguration
    static class KafkaProducerTestConfig {

        @Bean
        ProducerFactory<String, AuditEvent> auditEventTestProducerFactory(KafkaProperties kafkaProperties,
                                                                          ObjectMapper objectMapper) {
            Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), new JsonSerializer<>(objectMapper));
        }

        @Bean
        KafkaTemplate<String, AuditEvent> auditEventTestKafkaTemplate(
                ProducerFactory<String, AuditEvent> auditEventTestProducerFactory) {
            return new KafkaTemplate<>(auditEventTestProducerFactory);
        }
    }
}
