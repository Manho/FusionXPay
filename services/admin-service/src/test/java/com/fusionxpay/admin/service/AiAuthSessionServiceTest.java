package com.fusionxpay.admin.service;

import com.fusionxpay.ai.common.dto.auth.ai.AiAuthClientType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthFlowMode;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthGrantType;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthPollStatus;
import com.fusionxpay.ai.common.dto.auth.ai.AiAuthorizeRequest;
import com.fusionxpay.ai.common.dto.auth.ai.AiTokenExchangeRequest;
import com.fusionxpay.admin.AdminApplication;
import com.fusionxpay.admin.config.AiAuthProperties;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.model.AiAuthSession;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.repository.AiAuthSessionRepository;
import com.fusionxpay.admin.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class AiAuthSessionServiceTest {

    @Autowired
    private AiAuthSessionService service;

    @Autowired
    private AiAuthSessionRepository sessionRepository;

    @Autowired
    private AiAuthProperties properties;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void callbackFlowIssuesRefreshableSessionTokensAcrossAuthorizationExpiry() {
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

        var approved = service.approve(authorize.getSessionId(), null, merchant);
        String code = approved.getRedirectUrl().replaceAll(".*[?&]code=([^&]+).*", "$1");

        var token = service.exchange(AiTokenExchangeRequest.builder()
                .grantType(AiAuthGrantType.AUTHORIZATION_CODE)
                .code(code)
                .codeVerifier("verifier-123")
                .build());

        AiAuthSession persisted = sessionRepository.findByRefreshToken(token.getRefreshToken()).orElseThrow();
        persisted.setAuthorizationExpiresAt(Instant.now().minusSeconds(60));
        sessionRepository.saveAndFlush(persisted);

        var refreshed = service.refresh(token.getRefreshToken());

        assertThat(refreshed.getRefreshToken()).isNotEqualTo(token.getRefreshToken());
        assertThat(refreshed.getAudience()).isEqualTo("ai-cli");
        assertThat(refreshed.getMerchant().getId()).isEqualTo(171L);
    }

    @Test
    void refreshTokenExpiryStillStopsSessionAfterRefreshWindow() {
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

        String code = service.approve(authorize.getSessionId(), null, merchant)
                .getRedirectUrl()
                .replaceAll(".*[?&]code=([^&]+).*", "$1");

        var token = service.exchange(AiTokenExchangeRequest.builder()
                .grantType(AiAuthGrantType.AUTHORIZATION_CODE)
                .code(code)
                .codeVerifier("verifier-123")
                .build());

        AiAuthSession persisted = sessionRepository.findByRefreshToken(token.getRefreshToken()).orElseThrow();
        persisted.setRefreshTokenExpiresAt(Instant.now().minusSeconds(1));
        sessionRepository.saveAndFlush(persisted);

        assertThatThrownBy(() -> service.refresh(token.getRefreshToken()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refresh token expired");
    }

    @Test
    void deviceCodeFlowPersistsAcrossServiceInstances() {
        MerchantInfo merchant = merchant();

        var authorize = service.authorize(AiAuthorizeRequest.builder()
                .clientType(AiAuthClientType.MCP)
                .audience("ai-mcp")
                .flowMode(AiAuthFlowMode.DEVICE_CODE)
                .build());

        AiAuthSessionService restartedService = new AiAuthSessionService(properties, jwtTokenProvider, sessionRepository);

        assertThat(restartedService.poll(authorize.getDeviceCode()).getStatus())
                .isEqualTo(AiAuthPollStatus.AUTHORIZATION_PENDING);

        var consent = restartedService.getConsent(null, authorize.getUserCode(), merchant);
        assertThat(consent.getUserCode()).isEqualTo(authorize.getUserCode());

        restartedService.approve(null, authorize.getUserCode(), merchant);

        assertThat(restartedService.poll(authorize.getDeviceCode()).getStatus())
                .isEqualTo(AiAuthPollStatus.APPROVED);

        var token = restartedService.exchange(AiTokenExchangeRequest.builder()
                .grantType(AiAuthGrantType.DEVICE_CODE)
                .deviceCode(authorize.getDeviceCode())
                .build());

        assertThat(token.getAudience()).isEqualTo("ai-mcp");
        assertThat(token.getMerchant().getMerchantName()).isEqualTo("Demo Merchant");
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
