package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod httpMethod = exchange.getRequest().getMethod();
        String method = httpMethod != null ? httpMethod.name() : "UNKNOWN";
        log.info("Processing {} request to path: {}", method, path);
        
        exchange.getRequest().getHeaders().forEach((name, values) -> {
            values.forEach(value -> log.info("Header: {} = {}", name, value));
        });

        // Bypass API key validation for endpoints that are called by browsers or external providers
        // (admin routes use JWT, not API key), and allow CORS preflight to pass through.
        if (HttpMethod.OPTIONS.equals(httpMethod) ||
            path.startsWith("/api/v1/auth/") ||
            path.startsWith("/api/v1/admin/") ||
            // Payment provider callbacks/webhooks cannot include our API key header.
            path.startsWith("/api/v1/payment/webhook/") ||
            path.startsWith("/api/v1/payment/paypal/") ||
            path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/swagger-resources") ||
            path.startsWith("/webjars/")) {
                log.info("Bypassing API key validation for endpoint: {}", path);
                return chain.filter(exchange);
        }

        // Get the API key from the header
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        log.info("API Key in request: {}", apiKey != null ? apiKey : "Missing");
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Missing API key in request to {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Offload the blocking call to the boundedElastic scheduler
        return Mono.fromCallable(() -> {
                    Optional<User> userOpt = userService.getUserByApiKey(apiKey);
                    log.debug("Lookup for API key [{}] returned: {}", apiKey, userOpt);
                    return userOpt;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userOpt -> {
                    if (userOpt.isEmpty()) {
                        log.warn("Invalid API key [{}] for request to {}", apiKey, path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }
                    // Propagate merchant identity downstream so services can enforce basic RBAC.
                    // This is intentionally a simple header-based mechanism suitable for a demo setup.
                    User user = userOpt.get();
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(exchange.getRequest().mutate()
                                    .header("X-Merchant-Id", String.valueOf(user.getId()))
                                    .build())
                            .build();
                    return chain.filter(mutatedExchange);
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
