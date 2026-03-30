package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.common.security.JwtClaims;
import com.fusionxpay.common.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (shouldBypass(path, method)) {
            return chain.filter(exchange);
        }

        String token = extractToken(exchange);
        if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
            log.warn("Missing or invalid JWT for request to {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        JwtClaims claims = jwtUtils.parseClaims(token);
        if (claims.merchantId() == null || !StringUtils.hasText(claims.role())) {
            log.warn("Incomplete JWT claims for request to {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-Merchant-Id", String.valueOf(claims.merchantId()))
                        .header("X-Merchant-Role", claims.role())
                        .build())
                .build();
        return chain.filter(mutatedExchange);
    }

    private boolean shouldBypass(String path, HttpMethod method) {
        return HttpMethod.OPTIONS.equals(method)
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/payment/webhook/")
                || path.startsWith("/api/v1/payment/paypal/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars/");
    }

    private String extractToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
