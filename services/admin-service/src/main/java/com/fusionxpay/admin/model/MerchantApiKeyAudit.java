package com.fusionxpay.admin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit log for merchant API key actions.
 */
@Entity
@Table(name = "merchant_api_key_audit", indexes = {
        @Index(name = "idx_merchant_api_key_audit_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_merchant_api_key_audit_api_key_id", columnList = "api_key_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantApiKeyAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(name = "actor_merchant_id")
    private Long actorMerchantId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
