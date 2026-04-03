package com.fusionxpay.api.gateway.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.common.audit.PlatformAuditEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAuditConnectPayloadFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PlatformAuditConnectPayloadFactory factory = new PlatformAuditConnectPayloadFactory(objectMapper);

    @Test
    void shouldBuildKafkaConnectCompatiblePayload() throws Exception {
        Instant timestamp = Instant.parse("2026-04-03T08:09:10Z");
        PlatformAuditEvent event = PlatformAuditEvent.builder()
                .eventId("evt-123")
                .timestamp(timestamp)
                .correlationId("corr-456")
                .source("CLI-Java")
                .actionName("payment.search")
                .merchantId(4242L)
                .audience("merchant")
                .httpMethod("GET")
                .path("/api/v1/payment/search")
                .statusCode(200)
                .durationMs(123L)
                .build();

        JsonNode root = objectMapper.readTree(factory.toConnectJson(event));

        assertThat(root.path("schema").path("type").asText()).isEqualTo("struct");
        assertThat(root.path("schema").path("name").asText()).isEqualTo("com.fusionxpay.audit.PlatformAuditRecord");
        assertThat(root.path("schema").path("fields")).anySatisfy(field -> {
            assertThat(field.path("field").asText()).isEqualTo("timestamp");
            assertThat(field.path("name").asText()).isEqualTo("org.apache.kafka.connect.data.Timestamp");
        });
        assertThat(root.path("payload").path("event_id").asText()).isEqualTo("evt-123");
        assertThat(root.path("payload").path("timestamp").asLong()).isEqualTo(timestamp.toEpochMilli());
        assertThat(root.path("payload").path("merchant_id").asLong()).isEqualTo(4242L);
        assertThat(root.path("payload").path("status_code").asInt()).isEqualTo(200);
    }

    @Test
    void shouldPreserveNullOptionalFields() throws Exception {
        PlatformAuditEvent event = PlatformAuditEvent.builder()
                .eventId("evt-123")
                .timestamp(Instant.parse("2026-04-03T08:09:10Z"))
                .correlationId("corr-456")
                .source("UNKNOWN")
                .actionName("GET /health")
                .path("/actuator/health")
                .build();

        JsonNode root = objectMapper.readTree(factory.toConnectJson(event));

        assertThat(root.path("payload").path("merchant_id").isNull()).isTrue();
        assertThat(root.path("payload").path("audience").isNull()).isTrue();
        assertThat(root.path("payload").path("status_code").isNull()).isTrue();
    }
}
