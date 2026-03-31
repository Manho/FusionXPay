package com.fusionxpay.ai.common.audit;

import com.fusionxpay.ai.common.config.AuditProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@RequiredArgsConstructor
public class KafkaAuditEventPublisher implements AuditEventPublisher {

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final AuditProperties auditProperties;

    @Override
    public void publish(AuditEvent event) {
        try {
            kafkaTemplate.send(auditProperties.getTopic(), event.getCorrelationId(), event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("Failed to publish audit event {}", event.getEventId(), throwable);
                            return;
                        }
                        log.debug("Published audit event {} to topic {}", event.getEventId(), auditProperties.getTopic());
                    });
        } catch (RuntimeException ex) {
            log.warn("Failed to enqueue audit event {}", event.getEventId(), ex);
        }
    }
}
