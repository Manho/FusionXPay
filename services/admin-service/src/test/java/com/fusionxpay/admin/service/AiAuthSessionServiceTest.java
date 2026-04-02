package com.fusionxpay.admin.service;

import com.fusionxpay.ai.common.dto.auth.ai.AiAuthClientType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthFlowMode;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthGrantType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus;
import com.fusionxpay.ai.common.dto.auth.ai.AiPollRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiRefreshRequest;
import com.fusionxpay.admin.config.AiAuthProperties;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiAuthSessionServiceTest {

    private static final String SECRET = "fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long";

    @Test
    void callbackFlowIssuesRefreshableSessionTokens() {
        AiAuthSessionService service = new AiAuthSessionService(properties(), new JwtTokenProvider(SECRET, 86_400_000L));
        MerchantInfo merchant = merchant();

        var authorize = service.authorize(AiAuthorizeRequest.builder()
                .clientType(AiAuthClientType.CLI)
                .audience("ai-cli")
                .flowMode(AiAuthFlowMode.CALLBACK)
                .callbackUrl("http://127.0.0.1:38080/callback")
                .codeChallenge("verifier-123")
                .codeChallengeMethod("plain")
                .state("state-1")
                .build());

        assertThat(authorize.getAuthorizationUrl()).contains("/ai/consent?session=");

        var consent = service.getConsent(authorize.getSessionId(), null, merchant);
        assertThat(consent.getAudience()).isEqualTo("ai-cli");

        var approved = service.approve(authorize.getSessionId(), null, merchant);
        assertThat(approved.getRedirectUrl()).contains("code=");

        String code = approved.getRedirectUrl().replaceAll(".*[?&]code=([^&]+).*", "$1");
        var token = service.exchange(com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest.builder()
                .grantType(AiAuthGrantType.AUTHORIZATION_CODE)
                .code(code)
                .codeVerifier("verifier-123")
                .build());

        assertThat(token.getAudience()).isEqualTo("ai-cli");
        assertThat(token.getRefreshToken()).isNotBlank();
        assertThat(token.getMerchant().getId()).isEqualTo(171L);

        var refreshed = service.refresh(token.getRefreshToken());
        assertThat(refreshed.getRefreshToken()).isNotEqualTo(token.getRefreshToken());

        service.revoke(refreshed.getRefreshToken());
        assertThatThrownBy(() -> service.refresh(refreshed.getRefreshToken()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void deviceCodeFlowTransitionsFromPendingToApproved() {
        AiAuthSessionService service = new AiAuthSessionService(properties(), new JwtTokenProvider(SECRET, 86_400_000L));
        MerchantInfo merchant = merchant();

        var authorize = service.authorize(AiAuthorizeRequest.builder()
                .clientType(AiAuthClientType.MCP)
                .audience("ai-mcp")
                .flowMode(AiAuthFlowMode.DEVICE_CODE)
                .build());

        assertThat(service.poll(authorize.getDeviceCode()).getStatus()).isEqualTo(AiAuthPollStatus.AUTHORIZATION_PENDING);

        var consent = service.getConsent(null, authorize.getUserCode(), merchant);
        assertThat(consent.getUserCode()).isEqualTo(authorize.getUserCode());

        service.approve(null, authorize.getUserCode(), merchant);

        assertThat(service.poll(authorize.getDeviceCode()).getStatus()).isEqualTo(AiAuthPollStatus.APPROVED);

        var token = service.exchange(com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest.builder()
                .grantType(AiAuthGrantType.DEVICE_CODE)
                .deviceCode(authorize.getDeviceCode())
                .build());

        assertThat(token.getAudience()).isEqualTo("ai-mcp");
        assertThat(token.getMerchant().getMerchantName()).isEqualTo("Demo Merchant");
    }

    private AiAuthProperties properties() {
        AiAuthProperties properties = new AiAuthProperties();
        properties.setFrontendBaseUrl(URI.create("http://localhost:3000"));
        properties.setAllowedAudiences(List.of("ai-cli", "ai-mcp"));
        properties.setAllowedCallbackHosts(List.of("127.0.0.1", "localhost"));
        return properties;
    }

    private MerchantInfo merchant() {
        return MerchantInfo.builder()
                .id(171L)
                .merchantCode("MCH171")
                .merchantName("Demo Merchant")
                .email("merchant@example.com")
                .role(MerchantRole.MERCHANT)
                .build();
    }
}
