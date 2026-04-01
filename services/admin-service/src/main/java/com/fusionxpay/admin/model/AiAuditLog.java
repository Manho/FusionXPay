package com.fusionxpay.admin.model;

import com.fusionxpay.ai.common.audit.AuditSource;
import com.fusionxpay.ai.common.audit.AuditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ai_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private AuditSource source;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "action_name", nullable = false, length = 64)
    private String actionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AuditStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "input_summary", columnDefinition = "TEXT")
    private String inputSummary;

    @Column(name = "output_summary", columnDefinition = "TEXT")
    private String outputSummary;

    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
