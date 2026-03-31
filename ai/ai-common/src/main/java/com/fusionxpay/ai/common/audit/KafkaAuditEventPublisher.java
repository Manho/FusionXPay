package com.fusionxpay.ai.common.audit;

import com.fusionxpay.ai.common.config.AuditProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class KafkaAuditEventPublisher implements AuditEventPublisher {

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final AuditProperties auditProperties;

    @Override
    public void publish(AuditEvent event) {
        try {
            var sendFuture = kafkaTemplate.send(auditProperties.getTopic(), event.getCorrelationId(), event);
            if (auditProperties.isReliable()) {
                publishReliably(event, sendFuture);
                return;
            }
            sendFuture.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.warn("Failed to publish audit event {}: {}", event.getEventId(), failureSummary(throwable));
                    return;
                }
                log.debug("Published audit event {} to topic {}", event.getEventId(), auditProperties.getTopic());
            });
        } catch (RuntimeException ex) {
            log.warn("Failed to enqueue audit event {}: {}", event.getEventId(), failureSummary(ex));
        }
    }

    private void publishReliably(AuditEvent event,
                                 java.util.concurrent.CompletableFuture<?> sendFuture) {
        try {
            sendFuture.get(auditProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            kafkaTemplate.flush();
            log.debug("Published audit event {} to topic {} with reliable mode",
                    event.getEventId(),
                    auditProperties.getTopic());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for audit event {}: {}", event.getEventId(), failureSummary(ex));
        } catch (ExecutionException | TimeoutException ex) {
            log.warn("Failed to publish audit event {} in reliable mode: {}", event.getEventId(), failureSummary(ex));
        } catch (RuntimeException ex) {
            log.warn("Failed to flush audit event {} in reliable mode: {}", event.getEventId(), failureSummary(ex));
        }
    }

    private String failureSummary(Throwable throwable) {
        Throwable cause = throwable instanceof ExecutionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
