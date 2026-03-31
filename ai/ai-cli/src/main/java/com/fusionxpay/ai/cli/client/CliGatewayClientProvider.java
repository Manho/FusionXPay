package com.fusionxpay.ai.cli.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.config.FusionXGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class CliGatewayClientProvider {

    private final CliConfigStore configStore;
    private final FusionXGatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    public GatewayClient currentClient() {
        return clientFor(resolveBaseUrl());
    }

    public GatewayClient clientFor(URI baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(gatewayProperties.getConnectTimeout());
        requestFactory.setReadTimeout(gatewayProperties.getReadTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
        return new GatewayClient(restClient, objectMapper);
    }

    public URI resolveBaseUrl() {
        return configStore.load()
                .map(CliStoredConfig::getBaseUrl)
                .filter(value -> value != null && !value.isBlank())
                .map(URI::create)
                .orElse(gatewayProperties.getBaseUrl());
    }
}
