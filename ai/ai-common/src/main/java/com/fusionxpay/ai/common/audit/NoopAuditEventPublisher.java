package com.fusionxpay.ai.common.audit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoopAuditEventPublisher implements AuditEventPublisher {

    @Override
    public void publish(AuditEvent event) {
        log.debug("Audit publishing disabled, skipping event {}", event.getEventId());
    }
}
