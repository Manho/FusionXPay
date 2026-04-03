package com.fusionxpay.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAuditEvent {

    private String eventId;
    private Instant timestamp;
    private String correlationId;
    private String source;
    private String actionName;
    private Long merchantId;
    private String audience;
    private String httpMethod;
    private String path;
    private Integer statusCode;
    private Long durationMs;
}
