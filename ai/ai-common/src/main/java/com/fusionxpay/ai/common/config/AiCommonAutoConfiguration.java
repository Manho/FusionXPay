package com.fusionxpay.ai.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditRequestMetadataProvider;
import com.fusionxpay.ai.common.audit.ThreadLocalAuditRequestMetadataProvider;
import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.service.ConfirmationService;
import com.fusionxpay.ai.common.service.InMemoryConfirmationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@EnableConfigurationProperties({FusionXGatewayProperties.class, ConfirmationProperties.class})
public class AiCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "fusionxGatewayRestClient")
    RestClient fusionxGatewayRestClient(FusionXGatewayProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    AuditRequestMetadataProvider auditRequestMetadataProvider() {
        return new ThreadLocalAuditRequestMetadataProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayClient gatewayClient(RestClient fusionxGatewayRestClient,
                                ObjectMapper objectMapper,
                                AuditRequestMetadataProvider auditRequestMetadataProvider) {
        return new GatewayClient(fusionxGatewayRestClient, objectMapper, auditRequestMetadataProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    ConfirmationService confirmationService(ConfirmationProperties confirmationProperties) {
        return new InMemoryConfirmationService(confirmationProperties);
    }
}
