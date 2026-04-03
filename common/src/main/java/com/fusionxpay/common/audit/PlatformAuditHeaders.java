package com.fusionxpay.common.audit;

public final class PlatformAuditHeaders {

    public static final String AUDIT_SOURCE = "X-Audit-Source";
    public static final String AUDIT_ACTION = "X-Audit-Action";
    public static final String AUDIT_CORRELATION_ID = "X-Audit-Correlation-Id";
    public static final String MERCHANT_ID = "X-Merchant-Id";
    public static final String TOKEN_AUDIENCE = "X-Token-Audience";

    private PlatformAuditHeaders() {
    }
}
