package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.audit.PlatformAuditPublisher;
import com.fusionxpay.common.audit.PlatformAuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuditFilterTest {

    @Test
    void shouldPublishAuditEventWithInboundHeaders() {
        AtomicReference<PlatformAuditEvent> captured = new AtomicReference<>();
        GatewayAuditFilter filter = new GatewayAuditFilter(captured::set);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders")
                .header("X-Audit-Source", "CLI-Java")
                .header("X-Audit-Action", "order.search")
                .header("X-Audit-Correlation-Id", "corr-123")
                .build());
        exchange.getAttributes().put("fusionx.audit.merchantId", 42L);
        exchange.getAttributes().put("fusionx.audit.tokenAudience", "ai-cli");

        filter.filter(exchange, okChain(HttpStatus.OK)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getSource()).isEqualTo("CLI-Java");
        assertThat(captured.get().getActionName()).isEqualTo("order.search");
        assertThat(captured.get().getCorrelationId()).isEqualTo("corr-123");
        assertThat(captured.get().getMerchantId()).isEqualTo(42L);
        assertThat(captured.get().getAudience()).isEqualTo("ai-cli");
        assertThat(captured.get().getHttpMethod()).isEqualTo("GET");
        assertThat(captured.get().getPath()).isEqualTo("/api/v1/orders");
        assertThat(captured.get().getStatusCode()).isEqualTo(200);
        assertThat(captured.get().getDurationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void shouldFallbackWhenAuditHeadersAreMissing() {
        AtomicReference<PlatformAuditEvent> captured = new AtomicReference<>();
        GatewayAuditFilter filter = new GatewayAuditFilter(captured::set);
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/payment/request").build());

        filter.filter(exchange, okChain(HttpStatus.ACCEPTED)).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getSource()).isEqualTo("UNKNOWN");
        assertThat(captured.get().getActionName()).isEqualTo("POST /api/v1/payment/request");
        assertThat(captured.get().getCorrelationId()).isNotBlank();
        assertThat(captured.get().getStatusCode()).isEqualTo(202);
    }

    private GatewayFilterChain okChain(HttpStatus status) {
        return exchange -> {
            exchange.getResponse().setStatusCode(status);
            return Mono.empty();
        };
    }
}
