package com.fusionxpay.ai.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    private String eventId;
    private AuditSource source;
    private Long merchantId;
    private String actionName;
    private AuditStatus status;
    private Long durationMs;
    private String inputSummary;
    private String outputSummary;
    private Instant timestamp;
    private String conversationId;
    private String correlationId;
}
