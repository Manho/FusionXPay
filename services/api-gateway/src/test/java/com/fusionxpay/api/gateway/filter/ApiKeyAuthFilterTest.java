package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.model.User;
import com.fusionxpay.api.gateway.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private UserService userService;

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private ApiKeyAuthFilter apiKeyAuthFilter;

    private User testUser;
    private String validApiKey;

    @BeforeEach
    void setUp() {
        validApiKey = UUID.randomUUID().toString();
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedpassword")
                .apiKey(validApiKey)
                .roles("USER")
                .build();

        // Mock the filter chain to return a completed Mono
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Test that public endpoints bypass API key validation")
    void publicEndpointsAllowed() {
        // Test auth endpoint
        ServerWebExchange authExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/login").build());

        when(filterChain.filter(authExchange)).thenReturn(Mono.empty());
        apiKeyAuthFilter.filter(authExchange, filterChain).block();

        verify(filterChain).filter(authExchange);
        verify(userService, never()).getUserByApiKey(anyString());

        // Test swagger endpoint
        ServerWebExchange swaggerExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/swagger-ui/index.html").build());

        when(filterChain.filter(swaggerExchange)).thenReturn(Mono.empty());
        apiKeyAuthFilter.filter(swaggerExchange, filterChain).block();

        verify(filterChain).filter(swaggerExchange);
        verify(userService, never()).getUserByApiKey(anyString());
    }

    @Test
    @DisplayName("Test that protected endpoints require API key")
    void protectedEndpointsRequireApiKey() {
        // Create a request without API key
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/api/orders").build());

        apiKeyAuthFilter.filter(exchange, filterChain).block();

        // Verify that the response status is set to UNAUTHORIZED
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(exchange);
        verify(userService, never()).getUserByApiKey(anyString());
    }

    @Test
    @DisplayName("Test that valid API key allows access to protected endpoints")
    void validApiKeyAllowsAccess() {
        // Create a request with valid API key
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/api/orders")
                        .header("X-API-Key", validApiKey)
                        .build());

        when(userService.getUserByApiKey(validApiKey)).thenReturn(Optional.of(testUser));
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        apiKeyAuthFilter.filter(exchange, filterChain).block();

        verify(userService).getUserByApiKey(validApiKey);
        verify(filterChain).filter(exchange);
    }

    @Test
    @DisplayName("Test that invalid API key blocks access to protected endpoints")
    void invalidApiKeyBlocksAccess() {
        String invalidApiKey = "invalid-api-key";

        // Create a request with invalid API key
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order-service/api/orders")
                        .header("X-API-Key", invalidApiKey)
                        .build());

        when(userService.getUserByApiKey(invalidApiKey)).thenReturn(Optional.empty());

        apiKeyAuthFilter.filter(exchange, filterChain).block();

        // Verify that the response status is set to UNAUTHORIZED
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(userService).getUserByApiKey(invalidApiKey);
        verify(filterChain, never()).filter(exchange);
    }
}
