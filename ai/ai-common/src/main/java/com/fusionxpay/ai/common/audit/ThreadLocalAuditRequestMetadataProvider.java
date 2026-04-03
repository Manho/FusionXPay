package com.fusionxpay.ai.common.audit;

import org.springframework.lang.Nullable;

public class ThreadLocalAuditRequestMetadataProvider implements AuditRequestMetadataProvider {

    private final ThreadLocal<AuditRequestMetadata> currentMetadata = new ThreadLocal<>();

    @Override
    @Nullable
    public AuditRequestMetadata currentMetadata() {
        return currentMetadata.get();
    }

    @Override
    public Scope withMetadata(AuditRequestMetadata metadata) {
        AuditRequestMetadata previous = currentMetadata.get();
        currentMetadata.set(metadata);
        return () -> {
            if (previous == null) {
                currentMetadata.remove();
                return;
            }
            currentMetadata.set(previous);
        };
    }
}
