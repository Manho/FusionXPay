package com.fusionxpay.ai.mcpserver.tool;

import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.MerchantSession;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import com.fusionxpay.ai.mcpserver.config.McpAuthProperties;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
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
            currentSession = authenticate();
            return currentSession;
        }
    }

    public boolean supportsRefresh() {
        return hasCredentials() && !hasJwtToken();
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
                    .merchant(merchant)
                    .refreshable(true)
                    .build();
        }

        throw new AiAuthenticationException(401,
                "MCP authentication is not configured. Set FUSIONX_JWT_TOKEN or FUSIONX_EMAIL/FUSIONX_PASSWORD.",
                null,
                null);
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
