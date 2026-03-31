package com.fusionxpay.ai.cli.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fusionxpay.ai.cli.client.CliGatewayClientProvider;
import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliProperties;
import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class CliSessionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void loginStatusAndLogoutManagePersistedSession() {
        CliProperties cliProperties = new CliProperties();
        cliProperties.setConfigPath(tempDir.resolve("config.json"));
        CliConfigStore store = new CliConfigStore(cliProperties, new ObjectMapper().registerModule(new JavaTimeModule()));

        GatewayClient gatewayClient = Mockito.mock(GatewayClient.class);
        GatewayMerchantInfo merchant = GatewayMerchantInfo.builder()
                .id(55L)
                .merchantName("CLI Merchant")
                .email("merchant@example.com")
                .role("MERCHANT")
                .build();
        when(gatewayClient.login("merchant@example.com", "TestPass123"))
                .thenReturn(GatewayLoginResponse.builder()
                        .token("jwt-token")
                        .expiresIn(3600L)
                        .merchant(merchant)
                        .build());
        when(gatewayClient.getCurrentMerchant(anyString())).thenReturn(merchant);

        CliGatewayClientProvider provider = Mockito.mock(CliGatewayClientProvider.class);
        when(provider.resolveBaseUrl()).thenReturn(URI.create("https://api.fusionx.fun"));
        when(provider.clientFor(URI.create("https://api.fusionx.fun"))).thenReturn(gatewayClient);
        when(provider.currentClient()).thenReturn(gatewayClient);

        CliSessionService sessionService = new CliSessionService(provider, store);

        var login = sessionService.login("merchant@example.com", "TestPass123");
        assertThat(login.config().getJwt()).isEqualTo("jwt-token");
        assertThat(store.load().orElseThrow().getMerchantId()).isEqualTo(55L);

        var status = sessionService.status();
        assertThat(status.configured()).isTrue();
        assertThat(status.authenticated()).isTrue();
        assertThat(status.config().getMerchantName()).isEqualTo("CLI Merchant");

        sessionService.logout();
        assertThat(store.load().orElseThrow().getJwt()).isNull();
    }
}
