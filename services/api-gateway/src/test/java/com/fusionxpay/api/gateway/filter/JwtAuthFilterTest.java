package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.common.security.JwtClaims;
import com.fusionxpay.common.security.JwtUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    private static final String TEST_SECRET = "fusionxpay-admin-jwt-secret-key-must-be-at-least-256-bits-long";

    private final JwtUtils jwtUtils = new JwtUtils(TEST_SECRET);
    private final JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtUtils);

    @Test
    @DisplayName("Public endpoints bypass JWT validation")
    void publicEndpointsAllowed() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange authExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        jwtAuthFilter.filter(authExchange, chain).block();
        assertTrue(chain.wasCalled());

        TrackingFilterChain adminChain = new TrackingFilterChain();
        ServerWebExchange adminExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/admin/auth/login").build());

        jwtAuthFilter.filter(adminExchange, adminChain).block();
        assertTrue(adminChain.wasCalled());
    }

    @Test
    @DisplayName("OPTIONS requests bypass JWT validation")
    void optionsRequestsAllowed() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/orders").build());

        jwtAuthFilter.filter(exchange, chain).block();
        assertTrue(chain.wasCalled());
    }

    @Test
    @DisplayName("Protected endpoints require bearer token")
    void protectedEndpointsRequireJwt() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());

        jwtAuthFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Valid JWT injects merchant headers")
    void validJwtAllowsAccess() {
        String token = jwtUtils.generateToken(new JwtClaims(42L, "merchant@example.com", "MERCHANT"), 60_000);

        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .build());

        jwtAuthFilter.filter(exchange, chain).block();

        assertTrue(chain.wasCalled());
        assertEquals("42", chain.getForwardedHeader("X-Merchant-Id"));
        assertEquals("MERCHANT", chain.getForwardedHeader("X-Merchant-Role"));
    }

    @Test
    @DisplayName("Invalid JWT blocks access")
    void invalidJwtBlocksAccess() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders")
                        .header("Authorization", "Bearer invalid.jwt.token")
                        .build());

        jwtAuthFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private static class TrackingFilterChain implements GatewayFilterChain {
        private boolean called;
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.called = true;
            this.exchange = exchange;
            return Mono.empty();
        }

        boolean wasCalled() {
            return called;
        }

        String getForwardedHeader(String name) {
            return exchange.getRequest().getHeaders().getFirst(name);
        }
    }
}
