package com.fusionxpay.admin.config;

import com.fusionxpay.ai.common.audit.AuditEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAuditKafkaConfigTest {

    @Test
    @DisplayName("Should route failed audit events to the configured DLT topic")
    void shouldRouteFailedAuditEventsToConfiguredDltTopic() {
        AiAuditConsumerProperties properties = new AiAuditConsumerProperties();
        properties.setDltTopic("custom-ai-audit.DLT");

        @SuppressWarnings("unchecked")
        KafkaOperations<String, AuditEvent> operations = mock(KafkaOperations.class);
        @SuppressWarnings("unchecked")
        Consumer<String, AuditEvent> consumer = mock(Consumer.class);
        AiAuditKafkaConfig config = new AiAuditKafkaConfig();

        when(operations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        DeadLetterPublishingRecoverer recoverer = config.aiAuditDeadLetterPublishingRecoverer(operations, properties);
        clearInvocations(operations);

        ConsumerRecord<String, AuditEvent> record = new ConsumerRecord<>("ai-audit-log", 2, 0L, "key", null);
        recoverer.accept(record, consumer, new RuntimeException("boom"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        org.mockito.ArgumentCaptor<ProducerRecord<String, AuditEvent>> producerRecordCaptor =
                (org.mockito.ArgumentCaptor) org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        org.mockito.Mockito.verify(operations).send(producerRecordCaptor.capture());
        ProducerRecord<String, AuditEvent> producerRecord = producerRecordCaptor.getValue();
        assertThat(producerRecord.topic()).isEqualTo("custom-ai-audit.DLT");
    }
}
