package com.fusionxpay.ai.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldSerializeAndDeserializeAuditEventForKafka() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-123")
                .source(AuditSource.MCP)
                .merchantId(88L)
                .actionName("search_orders")
                .status(AuditStatus.SUCCESS)
                .durationMs(42L)
                .inputSummary("status=PAID")
                .outputSummary("1 order")
                .timestamp(Instant.parse("2026-03-31T03:00:00Z"))
                .conversationId("conv-1")
                .correlationId("corr-1")
                .build();

        JsonSerializer<AuditEvent> serializer = new JsonSerializer<>(objectMapper);
        JsonDeserializer<AuditEvent> deserializer = new JsonDeserializer<>(AuditEvent.class, objectMapper, false);

        byte[] payload = serializer.serialize("ai-audit-log", event);
        AuditEvent restored = deserializer.deserialize("ai-audit-log", payload);

        assertThat(restored).usingRecursiveComparison().isEqualTo(event);
    }
}
