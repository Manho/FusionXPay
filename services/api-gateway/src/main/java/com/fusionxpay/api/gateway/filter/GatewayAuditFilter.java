package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.audit.GatewayAuditAttributes;
import com.fusionxpay.api.gateway.audit.PlatformAuditPublisher;
import com.fusionxpay.common.audit.PlatformAuditEvent;
import com.fusionxpay.common.audit.PlatformAuditHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class GatewayAuditFilter implements GlobalFilter, Ordered {

    private static final String SOURCE_UNKNOWN = "UNKNOWN";

    private final PlatformAuditPublisher platformAuditPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAt = System.currentTimeMillis();
        String correlationId = headerOrDefault(exchange, PlatformAuditHeaders.AUDIT_CORRELATION_ID, UUID.randomUUID().toString());
        exchange.getResponse().getHeaders().set(PlatformAuditHeaders.AUDIT_CORRELATION_ID, correlationId);

        ServerWebExchange auditedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(headers -> headers.set(PlatformAuditHeaders.AUDIT_CORRELATION_ID, correlationId))
                        .build())
                .build();

        return chain.filter(auditedExchange)
                .doFinally(signalType -> publishEvent(auditedExchange, startedAt));
    }

    private void publishEvent(ServerWebExchange exchange, long startedAt) {
        try {
            String path = exchange.getRequest().getURI().getPath();
            HttpMethod method = exchange.getRequest().getMethod();
            String correlationId = headerOrDefault(exchange, PlatformAuditHeaders.AUDIT_CORRELATION_ID, UUID.randomUUID().toString());
            String source = headerOrDefault(exchange, PlatformAuditHeaders.AUDIT_SOURCE, SOURCE_UNKNOWN);
            String actionName = headerOrDefault(exchange, PlatformAuditHeaders.AUDIT_ACTION, fallbackActionName(method, path));

            platformAuditPublisher.publish(PlatformAuditEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .correlationId(correlationId)
                    .source(source)
                    .actionName(actionName)
                    .merchantId(resolveMerchantId(exchange))
                    .audience(resolveAudience(exchange))
                    .httpMethod(method == null ? null : method.name())
                    .path(path)
                    .statusCode(resolveStatusCode(exchange))
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to assemble platform audit event for {}", exchange.getRequest().getURI().getPath(), ex);
        }
    }

    private String fallbackActionName(@Nullable HttpMethod method, String path) {
        return (method == null ? "UNKNOWN" : method.name()) + " " + path;
    }

    private String headerOrDefault(ServerWebExchange exchange, String headerName, String fallback) {
        String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);
        return StringUtils.hasText(headerValue) ? headerValue : fallback;
    }

    @Nullable
    private Long resolveMerchantId(ServerWebExchange exchange) {
        Object merchantId = exchange.getAttribute(GatewayAuditAttributes.MERCHANT_ID);
        if (merchantId instanceof Long value) {
            return value;
        }
        String headerValue = exchange.getRequest().getHeaders().getFirst(PlatformAuditHeaders.MERCHANT_ID);
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        try {
            return Long.valueOf(headerValue);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private String resolveAudience(ServerWebExchange exchange) {
        Object audience = exchange.getAttribute(GatewayAuditAttributes.TOKEN_AUDIENCE);
        if (audience instanceof String value && StringUtils.hasText(value)) {
            return value;
        }
        String headerValue = exchange.getRequest().getHeaders().getFirst(PlatformAuditHeaders.TOKEN_AUDIENCE);
        return StringUtils.hasText(headerValue) ? headerValue : null;
    }

    @Nullable
    private Integer resolveStatusCode(ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        return statusCode == null ? null : statusCode.value();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
