package com.fusionxpay.api.gateway.config;

import com.fusionxpay.common.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
@RequiredArgsConstructor
public class RateLimitingConfig {

    private static final String CF_CONNECTING_IP = "Cf-Connecting-Ip";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

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
    public KeyResolver merchantIdKeyResolver() {
        return exchange -> {
            String authorization = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
            if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
                return Mono.empty();
            }

            String token = authorization.substring(BEARER_PREFIX.length());
            if (!jwtUtils.validateToken(token)) {
                return Mono.empty();
            }

            Long merchantId = jwtUtils.parseClaims(token).merchantId();
            return merchantId == null ? Mono.empty() : Mono.just(String.valueOf(merchantId));
        };
    }
}
