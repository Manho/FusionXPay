package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.service.ApiKeyValidationResult;
import com.fusionxpay.api.gateway.service.ApiKeyValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private final ApiKeyValidationService apiKeyValidationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod httpMethod = exchange.getRequest().getMethod();
        String method = httpMethod != null ? httpMethod.name() : "UNKNOWN";
        log.debug("Processing {} request to path: {}", method, path);

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

        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Missing API key in request to {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return apiKeyValidationService.resolveMerchant(apiKey)
                .flatMap(resultOpt -> {
                    if (resultOpt.isEmpty()) {
                        log.warn("Invalid API key [{}] for request to {}", apiKey, path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    ApiKeyValidationResult result = resultOpt.get();
                    var requestBuilder = exchange.getRequest().mutate()
                            .header("X-Merchant-Id", String.valueOf(result.merchantId()));
                    if (result.apiKeyId() != null) {
                        requestBuilder.header("X-Api-Key-Id", String.valueOf(result.apiKeyId()));
                    }
                    if (result.legacyMatched()) {
                        requestBuilder.header("X-Legacy-Key-Match", "true");
                    }

                    ServerWebExchange mutatedExchange = exchange.mutate().request(requestBuilder.build()).build();
                    return chain.filter(mutatedExchange);
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
