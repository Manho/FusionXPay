package com.fusionxpay.api.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimitRetryAfterFilter implements GlobalFilter, Ordered {

    private static final String RETRY_AFTER_SECONDS = "60";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
                    && !response.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)) {
                response.getHeaders().add(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
