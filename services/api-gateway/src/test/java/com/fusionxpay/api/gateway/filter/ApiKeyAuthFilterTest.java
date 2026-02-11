package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApiKeyAuthFilterTest {

    @Autowired
    private ApiKeyAuthFilter apiKeyAuthFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String cleanupUsername;

    @AfterEach
    void tearDown() {
        if (cleanupUsername != null) {
            userRepository.findByUsername(cleanupUsername).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("Public endpoints bypass API key validation")
    void publicEndpointsAllowed() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange authExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        apiKeyAuthFilter.filter(authExchange, chain).block();
        assertTrue(chain.wasCalled());

        TrackingFilterChain adminAuthChain = new TrackingFilterChain();
        ServerWebExchange adminAuthExchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/admin/auth/login").build());

        apiKeyAuthFilter.filter(adminAuthExchange, adminAuthChain).block();
        assertTrue(adminAuthChain.wasCalled());

        TrackingFilterChain swaggerChain = new TrackingFilterChain();
        ServerWebExchange swaggerExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/swagger-ui/index.html").build());

        apiKeyAuthFilter.filter(swaggerExchange, swaggerChain).block();
        assertTrue(swaggerChain.wasCalled());
    }

    @Test
    @DisplayName("Protected endpoints require API key")
    void protectedEndpointsRequireApiKey() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/api/orders").build());

        apiKeyAuthFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Valid API key allows access to protected endpoints")
    void validApiKeyAllowsAccess() {
        String apiKey = UUID.randomUUID().toString();
        cleanupUsername = "user-" + UUID.randomUUID();

        User user = User.builder()
                .username(cleanupUsername)
                .password(passwordEncoder.encode("testpassword"))
                .apiKey(apiKey)
                .roles("USER")
                .build();
        userRepository.save(user);

        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/api/orders")
                        .header("X-API-Key", apiKey)
                        .build());

        apiKeyAuthFilter.filter(exchange, chain).block();

        assertTrue(chain.wasCalled());
    }

    @Test
    @DisplayName("Invalid API key blocks access to protected endpoints")
    void invalidApiKeyBlocksAccess() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/api/orders")
                        .header("X-API-Key", "invalid-api-key")
                        .build());

        apiKeyAuthFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private static class TrackingFilterChain implements GatewayFilterChain {
        private boolean called;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            called = true;
            return Mono.empty();
        }

        boolean wasCalled() {
            return called;
        }
    }
}
