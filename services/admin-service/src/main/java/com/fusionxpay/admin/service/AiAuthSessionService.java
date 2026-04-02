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
import com.fusionxpay.admin.model.AiAuthSession;
import com.fusionxpay.admin.repository.AiAuthSessionRepository;
import com.fusionxpay.admin.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AiAuthSessionService {

    private static final String SESSION_TOKEN_TYPE = "interactive-session";

    private final AiAuthProperties properties;
    private final JwtTokenProvider jwtTokenProvider;
    private final AiAuthSessionRepository sessionRepository;

    public AiAuthorizeResponse authorize(AiAuthorizeRequest request) {
        cleanupExpiredSessions();
        validateAuthorizeRequest(request);

        Instant now = Instant.now();
        AiAuthSession session = AiAuthSession.builder()
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
                .authorizationExpiresAt(now.plus(properties.getSessionTtl()))
                .build();

        sessionRepository.save(session);

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
        AiAuthSession session = findSession(sessionId, userCode);
        ensureAuthorizationActive(session, Instant.now());

        return AiConsentViewResponse.builder()
                .sessionId(session.getSessionId())
                .userCode(session.getUserCode())
                .clientType(session.getClientType())
                .audience(session.getAudience())
                .flowMode(session.getFlowMode())
                .merchantEmail(merchantInfo.getEmail())
                .merchantName(merchantInfo.getMerchantName())
                .expiresIn(Math.max(0, session.getAuthorizationExpiresAt().getEpochSecond() - Instant.now().getEpochSecond()))
                .callbackDisplay(session.getCallbackUrl())
                .build();
    }

    public AiConsentApproveResponse approve(String sessionId, String userCode, MerchantInfo merchantInfo) {
        cleanupExpiredSessions();
        AiAuthSession session = findSession(sessionId, userCode);
        ensureAuthorizationActive(session, Instant.now());

        session.setMerchantId(merchantInfo.getId());
        session.setMerchantEmail(merchantInfo.getEmail());
        session.setMerchantName(merchantInfo.getMerchantName());
        session.setMerchantRole(merchantInfo.getRole() == null ? null : merchantInfo.getRole().name());
        session.setApprovedAt(Instant.now());

        if (session.getFlowMode() == AiAuthFlowMode.CALLBACK) {
            String authorizationCode = UUID.randomUUID().toString();
            session.setAuthorizationCode(authorizationCode);
            session.setAuthorizationCodeExpiresAt(Instant.now().plus(properties.getAuthorizationCodeTtl()));
            sessionRepository.save(session);
            return AiConsentApproveResponse.builder()
                    .flowMode(session.getFlowMode())
                    .approved(true)
                    .redirectUrl(buildCallbackRedirect(session, authorizationCode))
                    .message("Authorization approved. Returning to the local CLI callback.")
                    .build();
        }

        sessionRepository.save(session);
        return AiConsentApproveResponse.builder()
                .flowMode(session.getFlowMode())
                .approved(true)
                .message("Authorization approved. Return to your terminal to continue.")
                .build();
    }

    public AiPollResponse poll(String deviceCode) {
        cleanupExpiredSessions();
        AiAuthSession session = requireByDeviceCode(deviceCode);
        if (session.isAuthorizationExpired(Instant.now())) {
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

        AiAuthSession session = switch (request.getGrantType()) {
            case AUTHORIZATION_CODE -> validateAuthorizationCodeGrant(request);
            case DEVICE_CODE -> validateDeviceCodeGrant(request);
        };

        return issueTokens(session, Instant.now(), true);
    }

    public AiTokenResponse refresh(String refreshToken) {
        AiAuthSession session = requireByRefreshToken(refreshToken);
        Instant now = Instant.now();
        ensureRefreshable(session, now);
        AiTokenResponse response = issueTokens(session, now, false);
        cleanupExpiredSessions();
        return response;
    }

    public void revoke(String refreshToken) {
        AiAuthSession session = requireByRefreshToken(refreshToken);
        revokeInternal(session);
        sessionRepository.save(session);
        cleanupExpiredSessions();
    }

    private AiAuthSession validateAuthorizationCodeGrant(AiTokenExchangeRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        AiAuthSession session = requireByAuthorizationCode(request.getCode());
        Instant now = Instant.now();
        ensureApprovedForTokenExchange(session, now);
        if (session.getAuthorizationCodeExpiresAt() == null || session.getAuthorizationCodeExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Authorization code expired");
        }
        if (session.isAuthorizationCodeConsumed()) {
            throw new IllegalArgumentException("Authorization code already used");
        }
        if (session.getCodeChallenge() != null && !session.getCodeChallenge().isBlank()) {
            verifyPkce(session, request.getCodeVerifier());
        }
        session.setAuthorizationCodeConsumed(true);
        sessionRepository.save(session);
        return session;
    }

    private AiAuthSession validateDeviceCodeGrant(AiTokenExchangeRequest request) {
        if (request.getDeviceCode() == null || request.getDeviceCode().isBlank()) {
            throw new IllegalArgumentException("deviceCode is required");
        }
        AiAuthSession session = requireByDeviceCode(request.getDeviceCode());
        ensureApprovedForTokenExchange(session, Instant.now());
        return session;
    }

    private AiTokenResponse issueTokens(AiAuthSession session, Instant now, boolean requireAuthorizationWindow) {
        if (requireAuthorizationWindow) {
            ensureApprovedForTokenExchange(session, now);
        } else {
            ensureRefreshable(session, now);
        }
        String accessToken = jwtTokenProvider.generateToken(
                session.getMerchantId(),
                session.getMerchantEmail(),
                session.getMerchantRole(),
                session.getAudience(),
                SESSION_TOKEN_TYPE,
                properties.getAccessTokenTtl().toMillis()
        );
        String newRefreshToken = UUID.randomUUID().toString();
        session.setRefreshToken(newRefreshToken);
        session.setRefreshTokenExpiresAt(now.plus(properties.getRefreshTokenTtl()));
        sessionRepository.save(session);

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

    private void verifyPkce(AiAuthSession session, String codeVerifier) {
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

    private AiAuthSession findSession(String sessionId, String userCode) {
        if (sessionId != null && !sessionId.isBlank()) {
            return requireBySessionId(sessionId);
        }
        if (userCode != null && !userCode.isBlank()) {
            return requireByUserCode(userCode);
        }
        throw new IllegalArgumentException("sessionId or userCode is required");
    }

    private String buildAuthorizationUrl(AiAuthSession session) {
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

    private String buildCallbackRedirect(AiAuthSession session, String authorizationCode) {
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

    private AiAuthSession requireBySessionId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("AI auth session not found"));
    }

    private AiAuthSession requireByAuthorizationCode(String authorizationCode) {
        return sessionRepository.findByAuthorizationCode(authorizationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization code not found"));
    }

    private AiAuthSession requireByDeviceCode(String deviceCode) {
        return sessionRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new ResourceNotFoundException("Device code not found"));
    }

    private AiAuthSession requireByUserCode(String userCode) {
        return sessionRepository.findByUserCode(userCode)
                .orElseThrow(() -> new ResourceNotFoundException("User code not found"));
    }

    private AiAuthSession requireByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refreshToken is required");
        }
        return sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));
    }

    private void ensureAuthorizationActive(AiAuthSession session, Instant now) {
        if (session.isAuthorizationExpired(now)) {
            throw new IllegalArgumentException("AI auth session expired");
        }
        if (session.isRevoked()) {
            throw new IllegalArgumentException("AI auth session revoked");
        }
    }

    private void ensureApproved(AiAuthSession session) {
        if (session.getApprovedAt() == null || session.getMerchantId() == null) {
            throw new IllegalArgumentException("AI auth session is not approved yet");
        }
    }

    private void ensureApprovedForTokenExchange(AiAuthSession session, Instant now) {
        ensureAuthorizationActive(session, now);
        ensureApproved(session);
    }

    private void ensureRefreshable(AiAuthSession session, Instant now) {
        if (session.isRevoked()) {
            throw new IllegalArgumentException("AI auth session revoked");
        }
        ensureApproved(session);
        if (session.getRefreshTokenExpiresAt() == null || session.isRefreshExpired(now)) {
            revokeInternal(session);
            throw new IllegalArgumentException("Refresh token expired");
        }
    }

    private void revokeInternal(AiAuthSession session) {
        session.setRevoked(true);
    }

    private void cleanupExpiredSessions() {
        sessionRepository.deleteAllInBatch(sessionRepository.findExpiredSessions(Instant.now()));
    }
}
