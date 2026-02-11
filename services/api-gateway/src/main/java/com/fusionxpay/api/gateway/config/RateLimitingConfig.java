package com.fusionxpay.api.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitingConfig {

    private static final String CF_CONNECTING_IP = "Cf-Connecting-Ip";

    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // Cloudflare Tunnel sets Cf-Connecting-Ip to the real client IP.
            // This header is overwritten by Cloudflare edge and cannot be spoofed.
            String cfIp = exchange.getRequest().getHeaders().getFirst(CF_CONNECTING_IP);
            if (StringUtils.hasText(cfIp)) {
                return Mono.just(cfIp.split(",")[0].trim());
            }

            // Direct access (no Cloudflare) — use TCP remote address
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }
            return Mono.just("unknown-ip");
        };
    }

    @Bean
    public KeyResolver apiKeyKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
            if (StringUtils.hasText(apiKey)) {
                return Mono.just(apiKey);
            }
            // No API key → empty signals denial to the rate limiter
            return Mono.empty();
        };
    }
}
