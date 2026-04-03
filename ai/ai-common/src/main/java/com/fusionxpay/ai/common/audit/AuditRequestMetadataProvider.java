package com.fusionxpay.ai.common.audit;

import org.springframework.lang.Nullable;

public interface AuditRequestMetadataProvider {

    @Nullable
    AuditRequestMetadata currentMetadata();

    Scope withMetadata(AuditRequestMetadata metadata);

    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
