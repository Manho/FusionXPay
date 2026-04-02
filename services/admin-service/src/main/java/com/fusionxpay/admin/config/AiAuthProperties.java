package com.fusionxpay.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fusionx.ai.auth")
public class AiAuthProperties {

    private URI frontendBaseUrl = URI.create("http://localhost:3000");
    private String consentPath = "/ai/consent";
    private String devicePath = "/ai/device";
    private Duration sessionTtl = Duration.ofMinutes(10);
    private Duration authorizationCodeTtl = Duration.ofMinutes(2);
    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private Duration pollInterval = Duration.ofSeconds(3);
    private List<String> allowedAudiences = List.of("ai-cli", "ai-mcp");
    private List<String> allowedCallbackHosts = List.of("127.0.0.1", "localhost");
}
