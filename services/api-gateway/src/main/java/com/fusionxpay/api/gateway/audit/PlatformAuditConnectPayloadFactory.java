package com.fusionxpay.api.gateway.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fusionxpay.common.audit.PlatformAuditEvent;

import java.time.Instant;
import java.util.Objects;

public class PlatformAuditConnectPayloadFactory {

    private static final String ROOT_SCHEMA_NAME = "com.fusionxpay.audit.PlatformAuditRecord";
    private static final String TIMESTAMP_LOGICAL_NAME = "org.apache.kafka.connect.data.Timestamp";

    private final ObjectMapper objectMapper;

    public PlatformAuditConnectPayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toConnectJson(PlatformAuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        ObjectNode root = objectMapper.createObjectNode();
        root.set("schema", buildSchema());
        root.set("payload", buildPayload(event));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize platform audit event for Kafka Connect", ex);
        }
    }

    private ObjectNode buildSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "struct");
        schema.put("optional", false);
        schema.put("name", ROOT_SCHEMA_NAME);

        ArrayNode fields = objectMapper.createArrayNode();
        fields.add(stringField("event_id", false));
        fields.add(timestampField("timestamp", false));
        fields.add(stringField("correlation_id", false));
        fields.add(stringField("source", false));
        fields.add(stringField("action_name", false));
        fields.add(int64Field("merchant_id", true));
        fields.add(stringField("audience", true));
        fields.add(stringField("http_method", true));
        fields.add(stringField("path", false));
        fields.add(int32Field("status_code", true));
        fields.add(int64Field("duration_ms", true));
        schema.set("fields", fields);
        return schema;
    }

    private ObjectNode buildPayload(PlatformAuditEvent event) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("event_id", requireValue("eventId", event.getEventId()));
        payload.put("timestamp", requireValue("timestamp", event.getTimestamp()).toEpochMilli());
        payload.put("correlation_id", requireValue("correlationId", event.getCorrelationId()));
        payload.put("source", requireValue("source", event.getSource()));
        payload.put("action_name", requireValue("actionName", event.getActionName()));
        putNullableLong(payload, "merchant_id", event.getMerchantId());
        putNullableString(payload, "audience", event.getAudience());
        putNullableString(payload, "http_method", event.getHttpMethod());
        payload.put("path", requireValue("path", event.getPath()));
        putNullableInteger(payload, "status_code", event.getStatusCode());
        putNullableLong(payload, "duration_ms", event.getDurationMs());
        return payload;
    }

    private ObjectNode stringField(String fieldName, boolean optional) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("type", "string");
        field.put("optional", optional);
        field.put("field", fieldName);
        return field;
    }

    private ObjectNode int64Field(String fieldName, boolean optional) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("type", "int64");
        field.put("optional", optional);
        field.put("field", fieldName);
        return field;
    }

    private ObjectNode int32Field(String fieldName, boolean optional) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("type", "int32");
        field.put("optional", optional);
        field.put("field", fieldName);
        return field;
    }

    private ObjectNode timestampField(String fieldName, boolean optional) {
        ObjectNode field = int64Field(fieldName, optional);
        field.put("name", TIMESTAMP_LOGICAL_NAME);
        field.put("version", 1);
        return field;
    }

    private void putNullableString(ObjectNode target, String fieldName, String value) {
        if (value == null) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value);
    }

    private void putNullableLong(ObjectNode target, String fieldName, Long value) {
        if (value == null) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value);
    }

    private void putNullableInteger(ObjectNode target, String fieldName, Integer value) {
        if (value == null) {
            target.putNull(fieldName);
            return;
        }
        target.put(fieldName, value);
    }

    private <T> T requireValue(String fieldName, T value) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }
}
