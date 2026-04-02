package com.fusionxpay.ai.mcpserver.tool;

import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.MerchantSession;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthClientType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthFlowMode;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthGrantType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollResponse;
import com.fusionxpay.ai.common.dto.auth.ai.AiRefreshRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenResponse;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import com.fusionxpay.ai.mcpserver.config.McpAuthProperties;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.URI;

@Service
@Slf4j
@RequiredArgsConstructor
public class McpAuthenticationService {

    private final GatewayClient gatewayClient;
    private final McpAuthProperties authProperties;

    private volatile MerchantSession currentSession;

    public MerchantSession getCurrentSession() {
        MerchantSession session = currentSession;
        if (session != null) {
            return session;
        }
        synchronized (this) {
            if (currentSession == null) {
                currentSession = authenticate();
            }
            return currentSession;
        }
    }

    public MerchantSession refreshSession() {
        synchronized (this) {
            MerchantSession session = currentSession;
            if (session != null && session.getRefreshToken() != null && !session.getRefreshToken().isBlank()) {
                AiTokenResponse refreshed = gatewayClient.refreshAiToken(new AiRefreshRequest(session.getRefreshToken()));
                currentSession = MerchantSession.builder()
                        .token(refreshed.getAccessToken())
                        .refreshToken(refreshed.getRefreshToken())
                        .audience(refreshed.getAudience())
                        .tokenType(refreshed.getSessionTokenType())
                        .merchant(refreshed.getMerchant())
                        .refreshable(true)
                        .build();
            } else {
                currentSession = authenticate();
            }
            return currentSession;
        }
    }

    public boolean supportsRefresh() {
        return (currentSession != null && currentSession.getRefreshToken() != null && !currentSession.getRefreshToken().isBlank())
                || (hasCredentials() && !hasJwtToken());
    }

    @Nullable
    public Long getCurrentMerchantIdOrNull() {
        MerchantSession session = currentSession;
        return session == null || session.getMerchant() == null ? null : session.getMerchant().getId();
    }

    private MerchantSession authenticate() {
        if (hasJwtToken()) {
            String token = authProperties.getJwtToken().trim();
            GatewayMerchantInfo merchant = gatewayClient.getCurrentMerchant(token);
            validateMerchantRole(merchant);
            return MerchantSession.builder()
                    .token(token)
                    .merchant(merchant)
                    .refreshable(false)
                    .build();
        }

        if (hasCredentials()) {
            GatewayLoginResponse loginResponse = gatewayClient.login(authProperties.getEmail(), authProperties.getPassword());
            GatewayMerchantInfo merchant = loginResponse.getMerchant();
            if (merchant == null) {
                merchant = gatewayClient.getCurrentMerchant(loginResponse.getToken());
            }
            validateMerchantRole(merchant);
            return MerchantSession.builder()
                    .token(loginResponse.getToken())
                    .refreshToken(null)
                    .merchant(merchant)
                    .refreshable(true)
                    .build();
        }

        if (authProperties.isInteractiveEnabled()) {
            return authenticateWithDeviceCode();
        }

        throw new AiAuthenticationException(401,
                "MCP authentication is not configured. Set FUSIONX_JWT_TOKEN, FUSIONX_EMAIL/FUSIONX_PASSWORD, or enable browser auth.",
                null,
                null);
    }

    private MerchantSession authenticateWithDeviceCode() {
        AiAuthorizeResponse response = gatewayClient.authorizeAiSession(AiAuthorizeRequest.builder()
                .clientType(AiAuthClientType.MCP)
                .audience(authProperties.getAudience())
                .flowMode(AiAuthFlowMode.DEVICE_CODE)
                .build());

        log.warn("MCP browser authorization required.");
        log.warn("Open {} and approve code {}.", response.getVerificationUrl(), response.getUserCode());
        tryOpenBrowser(response.getAuthorizationUrl());

        long pollInterval = response.getPollIntervalSeconds() == null ? 3L : response.getPollIntervalSeconds();
        long deadline = System.currentTimeMillis() + 180_000L;
        while (System.currentTimeMillis() < deadline) {
            AiPollResponse pollResponse = gatewayClient.pollAiSession(new AiPollRequest(response.getDeviceCode()));
            if (pollResponse.getStatus() == com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus.APPROVED) {
                AiTokenResponse tokenResponse = gatewayClient.exchangeAiToken(AiTokenExchangeRequest.builder()
                        .grantType(AiAuthGrantType.DEVICE_CODE)
                        .deviceCode(response.getDeviceCode())
                        .build());
                validateMerchantRole(tokenResponse.getMerchant());
                return MerchantSession.builder()
                        .token(tokenResponse.getAccessToken())
                        .refreshToken(tokenResponse.getRefreshToken())
                        .audience(tokenResponse.getAudience())
                        .tokenType(tokenResponse.getSessionTokenType())
                        .merchant(tokenResponse.getMerchant())
                        .refreshable(true)
                        .build();
            }
            if (pollResponse.getStatus() == com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus.DENIED
                    || pollResponse.getStatus() == com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus.EXPIRED
                    || pollResponse.getStatus() == com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus.REVOKED) {
                throw new AiAuthenticationException(401, "MCP browser authorization failed: " + pollResponse.getStatus(), null, null);
            }
            sleepQuietly(pollInterval);
        }
        throw new AiAuthenticationException(401, "Timed out waiting for MCP browser authorization", null, null);
    }

    private void tryOpenBrowser(String authorizationUrl) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authorizationUrl));
            }
        } catch (Exception ex) {
            log.warn("Unable to open MCP authorization URL automatically: {}", authorizationUrl);
        }
    }

    private void sleepQuietly(long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiAuthenticationException(401, "MCP browser authorization interrupted", null, ex);
        }
    }

    private void validateMerchantRole(GatewayMerchantInfo merchant) {
        if (merchant == null || merchant.getId() == null) {
            throw new AiAuthenticationException(401, "Unable to resolve merchant identity for MCP session", null, null);
        }
        if (!"MERCHANT".equalsIgnoreCase(merchant.getRole())) {
            throw new AiAuthenticationException(403,
                    "MCP session must use a merchant account",
                    null,
                    null);
        }
    }

    private boolean hasJwtToken() {
        return authProperties.getJwtToken() != null && !authProperties.getJwtToken().isBlank();
    }

    private boolean hasCredentials() {
        return authProperties.getEmail() != null && !authProperties.getEmail().isBlank()
                && authProperties.getPassword() != null && !authProperties.getPassword().isBlank();
    }
}
