package com.fusionxpay.ai.common.audit;

import lombok.Builder;

@Builder
public record AuditRequestMetadata(
        String source,
        String actionName,
        String correlationId
) {
}
