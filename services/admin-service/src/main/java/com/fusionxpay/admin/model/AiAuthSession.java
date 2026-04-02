package com.fusionxpay.admin.model;

import com.fusionxpay.ai.common.dto.auth.ai.AiAuthClientType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthFlowMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "ai_auth_session",
        indexes = {
                @Index(name = "idx_ai_auth_session_auth_exp", columnList = "authorization_expires_at"),
                @Index(name = "idx_ai_auth_session_refresh_exp", columnList = "refresh_token_expires_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 16)
    private AiAuthClientType clientType;

    @Column(name = "audience", nullable = false, length = 32)
    private String audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_mode", nullable = false, length = 16)
    private AiAuthFlowMode flowMode;

    @Column(name = "callback_url", length = 1024)
    private String callbackUrl;

    @Column(name = "state_token", length = 128)
    private String state;

    @Column(name = "code_challenge", length = 255)
    private String codeChallenge;

    @Column(name = "code_challenge_method", length = 16)
    private String codeChallengeMethod;

    @Column(name = "user_code", unique = true, length = 32)
    private String userCode;

    @Column(name = "device_code", unique = true, length = 36)
    private String deviceCode;

    @Column(name = "authorization_expires_at", nullable = false)
    private Instant authorizationExpiresAt;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_email", length = 100)
    private String merchantEmail;

    @Column(name = "merchant_name", length = 100)
    private String merchantName;

    @Column(name = "merchant_role", length = 32)
    private String merchantRole;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "authorization_code", unique = true, length = 36)
    private String authorizationCode;

    @Column(name = "authorization_code_expires_at")
    private Instant authorizationCodeExpiresAt;

    @Column(name = "authorization_code_consumed", nullable = false)
    @Builder.Default
    private boolean authorizationCodeConsumed = false;

    @Column(name = "refresh_token", unique = true, length = 36)
    private String refreshToken;

    @Column(name = "refresh_token_expires_at")
    private Instant refreshTokenExpiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isAuthorizationExpired(Instant now) {
        return authorizationExpiresAt != null && authorizationExpiresAt.isBefore(now);
    }

    public boolean isRefreshExpired(Instant now) {
        return refreshTokenExpiresAt != null && refreshTokenExpiresAt.isBefore(now);
    }
}
