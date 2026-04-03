package com.fusionxpay.api.gateway.audit;

import com.fusionxpay.common.audit.PlatformAuditEvent;

public interface PlatformAuditPublisher {

    void publish(PlatformAuditEvent event);
}
