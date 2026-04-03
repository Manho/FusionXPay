package com.fusionxpay.api.gateway.audit;

import com.fusionxpay.api.gateway.config.PlatformAuditProperties;
import com.fusionxpay.common.audit.PlatformAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@RequiredArgsConstructor
public class KafkaPlatformAuditPublisher implements PlatformAuditPublisher {

    private final KafkaTemplate<String, PlatformAuditEvent> kafkaTemplate;
    private final PlatformAuditProperties properties;

    @Override
    public void publish(PlatformAuditEvent event) {
        try {
            kafkaTemplate.send(properties.getTopic(), event.getCorrelationId(), event)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("Failed to publish platform audit event {}", event.getEventId(), throwable);
                        }
                    });
        } catch (Exception ex) {
            log.warn("Failed to submit platform audit event {}", event.getEventId(), ex);
        }
    }
}
