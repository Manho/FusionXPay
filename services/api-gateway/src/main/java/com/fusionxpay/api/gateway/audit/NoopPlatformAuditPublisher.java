package com.fusionxpay.api.gateway.audit;

import com.fusionxpay.common.audit.PlatformAuditEvent;

public class NoopPlatformAuditPublisher implements PlatformAuditPublisher {

    @Override
    public void publish(PlatformAuditEvent event) {
        // Intentionally left blank when platform audit is disabled.
    }
}
