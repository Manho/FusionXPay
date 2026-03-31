package com.fusionxpay.ai.mcpserver.tool;

import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.MerchantSession;
import com.fusionxpay.ai.mcpserver.config.McpAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpAuthenticationServiceTest {

    @Test
    void shouldResolveMerchantFromJwtToken() {
        GatewayClient gatewayClient = mock(GatewayClient.class);
        McpAuthProperties properties = new McpAuthProperties();
        properties.setJwtToken("jwt-token");
        when(gatewayClient.getCurrentMerchant("jwt-token"))
                .thenReturn(GatewayMerchantInfo.builder()
                        .id(42L)
                        .merchantName("Demo Merchant")
                        .role("MERCHANT")
                        .build());

        McpAuthenticationService authenticationService = new McpAuthenticationService(gatewayClient, properties);

        MerchantSession session = authenticationService.getCurrentSession();

        assertThat(session.getToken()).isEqualTo("jwt-token");
        assertThat(session.getMerchant().getId()).isEqualTo(42L);
        assertThat(session.isRefreshable()).isFalse();
    }

    @Test
    void shouldLoginAndRefreshWithCredentials() {
        GatewayClient gatewayClient = mock(GatewayClient.class);
        McpAuthProperties properties = new McpAuthProperties();
        properties.setEmail("merchant@example.com");
        properties.setPassword("secret");

        when(gatewayClient.login("merchant@example.com", "secret"))
                .thenReturn(GatewayLoginResponse.builder()
                        .token("token-1")
                        .merchant(GatewayMerchantInfo.builder()
                                .id(77L)
                                .merchantName("Merchant 77")
                                .role("MERCHANT")
                                .build())
                        .build())
                .thenReturn(GatewayLoginResponse.builder()
                        .token("token-2")
                        .merchant(GatewayMerchantInfo.builder()
                                .id(77L)
                                .merchantName("Merchant 77")
                                .role("MERCHANT")
                                .build())
                        .build());

        McpAuthenticationService authenticationService = new McpAuthenticationService(gatewayClient, properties);

        MerchantSession initialSession = authenticationService.getCurrentSession();
        MerchantSession refreshedSession = authenticationService.refreshSession();

        assertThat(initialSession.getToken()).isEqualTo("token-1");
        assertThat(refreshedSession.getToken()).isEqualTo("token-2");
        assertThat(authenticationService.supportsRefresh()).isTrue();
        verify(gatewayClient, times(2)).login("merchant@example.com", "secret");
    }
}
