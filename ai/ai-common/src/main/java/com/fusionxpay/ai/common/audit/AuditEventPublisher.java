package com.fusionxpay.ai.common.audit;

public interface AuditEventPublisher {
    void publish(AuditEvent event);
}
