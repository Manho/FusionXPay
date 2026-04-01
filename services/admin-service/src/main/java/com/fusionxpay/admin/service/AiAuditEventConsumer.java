package com.fusionxpay.admin.service;

import com.fusionxpay.ai.common.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAuditEventConsumer {

    private final AiAuditLogService auditLogService;

    @KafkaListener(
            topics = "${fusionx.ai.audit.topic:ai-audit-log}",
            groupId = "${fusionx.ai.audit.consumer-group:admin-service-ai-audit}",
            containerFactory = "auditEventKafkaListenerContainerFactory"
    )
    public void consume(AuditEvent event,
                        @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        log.debug("Received AI audit event {} with key {}", event.getEventId(), key);
        auditLogService.persist(event);
    }
}
