package com.fusionxpay.ai.common.audit;

import com.fusionxpay.ai.common.config.AuditProperties;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaAuditEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, AuditEvent> kafkaTemplate = mock(KafkaTemplate.class);

    @Test
    void nonReliableModeDoesNotBlockOnIncompleteSend() {
        AuditProperties properties = new AuditProperties();
        properties.setReliable(false);

        when(kafkaTemplate.send(anyString(), anyString(), any(AuditEvent.class)))
                .thenReturn(new CompletableFuture<>());

        KafkaAuditEventPublisher publisher = new KafkaAuditEventPublisher(kafkaTemplate, properties);

        assertThatCode(() -> publisher.publish(sampleEvent()))
                .doesNotThrowAnyException();

        verify(kafkaTemplate, never()).flush();
    }

    @Test
    void reliableModeWaitsForSendAndFlushesWhenSuccessful() {
        AuditProperties properties = new AuditProperties();
        properties.setReliable(true);
        properties.setSendTimeout(Duration.ofMillis(50));

        CompletableFuture<SendResult<String, AuditEvent>> sendFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any(AuditEvent.class)))
                .thenReturn(sendFuture);

        KafkaAuditEventPublisher publisher = new KafkaAuditEventPublisher(kafkaTemplate, properties);

        assertThatCode(() -> publisher.publish(sampleEvent()))
                .doesNotThrowAnyException();

        verify(kafkaTemplate).flush();
    }

    @Test
    void reliableModeTimesOutWithoutBreakingCaller() {
        AuditProperties properties = new AuditProperties();
        properties.setReliable(true);
        properties.setSendTimeout(Duration.ofMillis(5));

        when(kafkaTemplate.send(anyString(), anyString(), any(AuditEvent.class)))
                .thenReturn(new CompletableFuture<>());

        KafkaAuditEventPublisher publisher = new KafkaAuditEventPublisher(kafkaTemplate, properties);

        assertThatCode(() -> publisher.publish(sampleEvent()))
                .doesNotThrowAnyException();

        verify(kafkaTemplate, never()).flush();
    }

    private AuditEvent sampleEvent() {
        return AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .actionName("cli.status")
                .status(AuditStatus.SUCCESS)
                .source(AuditSource.CLI)
                .build();
    }
}
