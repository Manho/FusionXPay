package com.fusionxpay.admin.service;

import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthClientType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthFlowMode;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthGrantType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiConsentApproveResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiConsentViewResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenResponse;
import com.fusionxpay.admin.config.AiAuthProperties;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.exception.ResourceNotFoundException;
import com.fusionxpay.admin.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AiAuthSessionService {

    private static final String SESSION_TOKEN_TYPE = "interactive-session";

    private final AiAuthProperties properties;
    private final JwtTokenProvider jwtTokenProvider;

    private final Map<String, SessionRecord> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByAuthorizationCode = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByDeviceCode = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByUserCode = new ConcurrentHashMap<>();
    private final Map<String, String> sessionIdByRefreshToken = new ConcurrentHashMap<>();

    public AiAuthorizeResponse authorize(AiAuthorizeRequest request) {
        cleanupExpiredSessions();
        validateAuthorizeRequest(request);

        Instant now = Instant.now();
        SessionRecord session = SessionRecord.builder()
                .sessionId(UUID.randomUUID().toString())
                .clientType(request.getClientType())
                .audience(request.getAudience())
                .flowMode(request.getFlowMode())
                .callbackUrl(request.getCallbackUrl())
                .state(request.getState())
                .codeChallenge(request.getCodeChallenge())
                .codeChallengeMethod(normalizeCodeChallengeMethod(request.getCodeChallengeMethod()))
                .deviceCode(request.getFlowMode() == AiAuthFlowMode.DEVICE_CODE ? UUID.randomUUID().toString() : null)
                .userCode(request.getFlowMode() == AiAuthFlowMode.DEVICE_CODE ? generateUserCode() : null)
                .expiresAt(now.plus(properties.getSessionTtl()))
                .build();

        sessionsById.put(session.getSessionId(), session);
        if (session.getDeviceCode() != null) {
            sessionIdByDeviceCode.put(session.getDeviceCode(), session.getSessionId());
        }
        if (session.getUserCode() != null) {
            sessionIdByUserCode.put(session.getUserCode(), session.getSessionId());
        }

        return AiAuthorizeResponse.builder()
                .sessionId(session.getSessionId())
                .flowMode(session.getFlowMode())
                .authorizationUrl(buildAuthorizationUrl(session))
                .verificationUrl(session.getFlowMode() == AiAuthFlowMode.DEVICE_CODE
                        ? properties.getFrontendBaseUrl().resolve(properties.getDevicePath()).toString()
                        : null)
                .deviceCode(session.getDeviceCode())
                .userCode(session.getUserCode())
                .expiresIn(properties.getSessionTtl().toSeconds())
                .pollIntervalSeconds(properties.getPollInterval().toSeconds())
                .build();
    }

    public AiConsentViewResponse getConsent(String sessionId, String userCode, MerchantInfo merchantInfo) {
        cleanupExpiredSessions();
        SessionRecord session = findSession(sessionId, userCode);
        ensureActiveSession(session);

        return AiConsentViewResponse.builder()
                .sessionId(session.getSessionId())
                .userCode(session.getUserCode())
                .clientType(session.getClientType())
                .audience(session.getAudience())
                .flowMode(session.getFlowMode())
                .merchantEmail(merchantInfo.getEmail())
                .merchantName(merchantInfo.getMerchantName())
                .expiresIn(Math.max(0, session.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond()))
                .callbackDisplay(session.getCallbackUrl())
                .build();
    }

    public AiConsentApproveResponse approve(String sessionId, String userCode, MerchantInfo merchantInfo) {
        cleanupExpiredSessions();
        SessionRecord session = findSession(sessionId, userCode);
        ensureActiveSession(session);

        session.setMerchantId(merchantInfo.getId());
        session.setMerchantEmail(merchantInfo.getEmail());
        session.setMerchantName(merchantInfo.getMerchantName());
        session.setMerchantRole(merchantInfo.getRole() == null ? null : merchantInfo.getRole().name());
        session.setApprovedAt(Instant.now());

        if (session.getFlowMode() == AiAuthFlowMode.CALLBACK) {
            String authorizationCode = UUID.randomUUID().toString();
            session.setAuthorizationCode(authorizationCode);
            session.setAuthorizationCodeExpiresAt(Instant.now().plus(properties.getAuthorizationCodeTtl()));
            sessionIdByAuthorizationCode.put(authorizationCode, session.getSessionId());
            return AiConsentApproveResponse.builder()
                    .flowMode(session.getFlowMode())
                    .approved(true)
                    .redirectUrl(buildCallbackRedirect(session, authorizationCode))
                    .message("Authorization approved. Returning to the local CLI callback.")
                    .build();
        }

        return AiConsentApproveResponse.builder()
                .flowMode(session.getFlowMode())
                .approved(true)
                .message("Authorization approved. Return to your terminal to continue.")
                .build();
    }

    public AiPollResponse poll(String deviceCode) {
        cleanupExpiredSessions();
        SessionRecord session = requireByDeviceCode(deviceCode);
        if (session.isExpired()) {
            return AiPollResponse.builder()
                    .status(AiAuthPollStatus.EXPIRED)
                    .pollIntervalSeconds(properties.getPollInterval().toSeconds())
                    .message("Device code expired")
                    .build();
        }
        if (session.isRevoked()) {
            return AiPollResponse.builder()
                    .status(AiAuthPollStatus.REVOKED)
                    .pollIntervalSeconds(properties.getPollInterval().toSeconds())
                    .message("Authorization revoked")
                    .build();
        }
        return AiPollResponse.builder()
                .status(session.getApprovedAt() == null ? AiAuthPollStatus.AUTHORIZATION_PENDING : AiAuthPollStatus.APPROVED)
                .pollIntervalSeconds(properties.getPollInterval().toSeconds())
                .message(session.getApprovedAt() == null ? "Awaiting browser approval" : "Authorization approved")
                .build();
    }

    public AiTokenResponse exchange(AiTokenExchangeRequest request) {
        cleanupExpiredSessions();
        if (request.getGrantType() == null) {
            throw new IllegalArgumentException("grantType is required");
        }

        SessionRecord session = switch (request.getGrantType()) {
            case AUTHORIZATION_CODE -> validateAuthorizationCodeGrant(request);
            case DEVICE_CODE -> validateDeviceCodeGrant(request);
        };

        return issueTokens(session);
    }

    public AiTokenResponse refresh(String refreshToken) {
        cleanupExpiredSessions();
        SessionRecord session = requireByRefreshToken(refreshToken);
        if (session.getRefreshTokenExpiresAt() == null || session.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            revokeInternal(session);
            throw new IllegalArgumentException("Refresh token expired");
        }
        return issueTokens(session);
    }

    public void revoke(String refreshToken) {
        cleanupExpiredSessions();
        SessionRecord session = requireByRefreshToken(refreshToken);
        revokeInternal(session);
    }

    private SessionRecord validateAuthorizationCodeGrant(AiTokenExchangeRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        SessionRecord session = requireByAuthorizationCode(request.getCode());
        ensureApprovedSession(session);
        if (session.getAuthorizationCodeExpiresAt() == null || session.getAuthorizationCodeExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Authorization code expired");
        }
        if (session.isAuthorizationCodeConsumed()) {
            throw new IllegalArgumentException("Authorization code already used");
        }
        if (session.getCodeChallenge() != null && !session.getCodeChallenge().isBlank()) {
            verifyPkce(session, request.getCodeVerifier());
        }
        session.setAuthorizationCodeConsumed(true);
        sessionIdByAuthorizationCode.remove(session.getAuthorizationCode());
        return session;
    }

    private SessionRecord validateDeviceCodeGrant(AiTokenExchangeRequest request) {
        if (request.getDeviceCode() == null || request.getDeviceCode().isBlank()) {
            throw new IllegalArgumentException("deviceCode is required");
        }
        SessionRecord session = requireByDeviceCode(request.getDeviceCode());
        ensureApprovedSession(session);
        return session;
    }

    private AiTokenResponse issueTokens(SessionRecord session) {
        ensureApprovedSession(session);

        String accessToken = jwtTokenProvider.generateToken(
                session.getMerchantId(),
                session.getMerchantEmail(),
                session.getMerchantRole(),
                session.getAudience(),
                SESSION_TOKEN_TYPE,
                properties.getAccessTokenTtl().toMillis()
        );
        String newRefreshToken = UUID.randomUUID().toString();
        if (session.getRefreshToken() != null) {
            sessionIdByRefreshToken.remove(session.getRefreshToken());
        }
        session.setRefreshToken(newRefreshToken);
        session.setRefreshTokenExpiresAt(Instant.now().plus(properties.getRefreshTokenTtl()));
        sessionIdByRefreshToken.put(newRefreshToken, session.getSessionId());

        return AiTokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(properties.getAccessTokenTtl().toSeconds())
                .refreshToken(newRefreshToken)
                .audience(session.getAudience())
                .sessionTokenType(SESSION_TOKEN_TYPE)
                .merchant(GatewayMerchantInfo.builder()
                        .id(session.getMerchantId())
                        .email(session.getMerchantEmail())
                        .merchantName(session.getMerchantName())
                        .role(session.getMerchantRole())
                        .build())
                .build();
    }

    private void verifyPkce(SessionRecord session, String codeVerifier) {
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("codeVerifier is required");
        }
        String expected = "S256".equalsIgnoreCase(session.getCodeChallengeMethod())
                ? sha256(codeVerifier)
                : codeVerifier;
        if (!expected.equals(session.getCodeChallenge())) {
            throw new IllegalArgumentException("Invalid code verifier");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private void validateAuthorizeRequest(AiAuthorizeRequest request) {
        if (request.getClientType() == null) {
            throw new IllegalArgumentException("clientType is required");
        }
        if (request.getFlowMode() == null) {
            throw new IllegalArgumentException("flowMode is required");
        }
        if (request.getAudience() == null || request.getAudience().isBlank()) {
            throw new IllegalArgumentException("audience is required");
        }
        if (!properties.getAllowedAudiences().contains(request.getAudience())) {
            throw new IllegalArgumentException("Unsupported audience: " + request.getAudience());
        }
        String expectedAudience = request.getClientType() == AiAuthClientType.CLI ? "ai-cli" : "ai-mcp";
        if (!expectedAudience.equals(request.getAudience())) {
            throw new IllegalArgumentException("Audience does not match clientType");
        }
        if (request.getFlowMode() == AiAuthFlowMode.CALLBACK) {
            validateCallbackRequest(request);
        }
    }

    private void validateCallbackRequest(AiAuthorizeRequest request) {
        if (request.getCallbackUrl() == null || request.getCallbackUrl().isBlank()) {
            throw new IllegalArgumentException("callbackUrl is required for callback flow");
        }
        URI callbackUri = URI.create(request.getCallbackUrl());
        if (!"http".equalsIgnoreCase(callbackUri.getScheme())) {
            throw new IllegalArgumentException("callbackUrl must use http");
        }
        if (callbackUri.getHost() == null || !properties.getAllowedCallbackHosts().contains(callbackUri.getHost())) {
            throw new IllegalArgumentException("callbackUrl host is not allowed");
        }
        if (request.getCodeChallenge() == null || request.getCodeChallenge().isBlank()) {
            throw new IllegalArgumentException("codeChallenge is required for callback flow");
        }
    }

    private String normalizeCodeChallengeMethod(String method) {
        return method == null || method.isBlank() ? "S256" : method;
    }

    private SessionRecord findSession(String sessionId, String userCode) {
        if (sessionId != null && !sessionId.isBlank()) {
            return requireBySessionId(sessionId);
        }
        if (userCode != null && !userCode.isBlank()) {
            return requireByUserCode(userCode);
        }
        throw new IllegalArgumentException("sessionId or userCode is required");
    }

    private String buildAuthorizationUrl(SessionRecord session) {
        if (session.getFlowMode() == AiAuthFlowMode.CALLBACK) {
            return properties.getFrontendBaseUrl()
                    .resolve(properties.getConsentPath() + "?session=" + session.getSessionId())
                    .toString();
        }
        String encodedUserCode = URLEncoder.encode(session.getUserCode(), StandardCharsets.UTF_8);
        return properties.getFrontendBaseUrl()
                .resolve(properties.getDevicePath() + "?user_code=" + encodedUserCode)
                .toString();
    }

    private String buildCallbackRedirect(SessionRecord session, String authorizationCode) {
        StringBuilder builder = new StringBuilder(session.getCallbackUrl());
        builder.append(session.getCallbackUrl().contains("?") ? "&" : "?")
                .append("code=").append(URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8));
        if (session.getState() != null && !session.getState().isBlank()) {
            builder.append("&state=").append(URLEncoder.encode(session.getState(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String generateUserCode() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }

    private SessionRecord requireBySessionId(String sessionId) {
        SessionRecord session = sessionsById.get(sessionId);
        if (session == null) {
            throw new ResourceNotFoundException("AI auth session not found");
        }
        return session;
    }

    private SessionRecord requireByAuthorizationCode(String authorizationCode) {
        String sessionId = sessionIdByAuthorizationCode.get(authorizationCode);
        if (sessionId == null) {
            throw new ResourceNotFoundException("Authorization code not found");
        }
        return requireBySessionId(sessionId);
    }

    private SessionRecord requireByDeviceCode(String deviceCode) {
        String sessionId = sessionIdByDeviceCode.get(deviceCode);
        if (sessionId == null) {
            throw new ResourceNotFoundException("Device code not found");
        }
        return requireBySessionId(sessionId);
    }

    private SessionRecord requireByUserCode(String userCode) {
        String sessionId = sessionIdByUserCode.get(userCode);
        if (sessionId == null) {
            throw new ResourceNotFoundException("User code not found");
        }
        return requireBySessionId(sessionId);
    }

    private SessionRecord requireByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }
        String sessionId = sessionIdByRefreshToken.get(refreshToken);
        if (sessionId == null) {
            throw new ResourceNotFoundException("Refresh token not found");
        }
        return requireBySessionId(sessionId);
    }

    private void ensureActiveSession(SessionRecord session) {
        if (session.isExpired()) {
            throw new IllegalArgumentException("AI auth session expired");
        }
        if (session.isRevoked()) {
            throw new IllegalArgumentException("AI auth session revoked");
        }
    }

    private void ensureApprovedSession(SessionRecord session) {
        ensureActiveSession(session);
        if (session.getApprovedAt() == null || session.getMerchantId() == null) {
            throw new IllegalArgumentException("AI auth session is not approved yet");
        }
    }

    private void revokeInternal(SessionRecord session) {
        session.setRevoked(true);
        if (session.getRefreshToken() != null) {
            sessionIdByRefreshToken.remove(session.getRefreshToken());
        }
        if (session.getAuthorizationCode() != null) {
            sessionIdByAuthorizationCode.remove(session.getAuthorizationCode());
        }
    }

    private void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessionsById.values().removeIf(session -> {
            boolean expired = session.getExpiresAt().isBefore(now)
                    || (session.getRefreshTokenExpiresAt() != null && session.getRefreshTokenExpiresAt().isBefore(now) && session.getApprovedAt() != null);
            if (!expired) {
                return false;
            }
            if (session.getDeviceCode() != null) {
                sessionIdByDeviceCode.remove(session.getDeviceCode());
            }
            if (session.getUserCode() != null) {
                sessionIdByUserCode.remove(session.getUserCode());
            }
            if (session.getAuthorizationCode() != null) {
                sessionIdByAuthorizationCode.remove(session.getAuthorizationCode());
            }
            if (session.getRefreshToken() != null) {
                sessionIdByRefreshToken.remove(session.getRefreshToken());
            }
            return true;
        });
    }

    @lombok.Data
    @lombok.Builder
    private static class SessionRecord {
        private String sessionId;
        private AiAuthClientType clientType;
        private String audience;
        private AiAuthFlowMode flowMode;
        private String callbackUrl;
        private String state;
        private String codeChallenge;
        private String codeChallengeMethod;
        private String userCode;
        private String deviceCode;
        private Instant expiresAt;
        private Long merchantId;
        private String merchantEmail;
        private String merchantName;
        private String merchantRole;
        private Instant approvedAt;
        private String authorizationCode;
        private Instant authorizationCodeExpiresAt;
        private boolean authorizationCodeConsumed;
        private String refreshToken;
        private Instant refreshTokenExpiresAt;
        private boolean revoked;

        boolean isExpired() {
            return expiresAt != null && expiresAt.isBefore(Instant.now());
        }
    }
}
