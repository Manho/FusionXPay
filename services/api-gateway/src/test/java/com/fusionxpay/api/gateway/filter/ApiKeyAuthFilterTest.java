package com.fusionxpay.api.gateway.filter;

import com.fusionxpay.api.gateway.model.MerchantAccount;
import com.fusionxpay.api.gateway.model.MerchantApiKeyRecord;
import com.fusionxpay.api.gateway.model.MerchantStatus;
import com.fusionxpay.api.gateway.repository.MerchantAccountRepository;
import com.fusionxpay.api.gateway.repository.MerchantApiKeyRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ApiKeyAuthFilterTest {

    @Autowired
    private ApiKeyAuthFilter apiKeyAuthFilter;

    @Autowired
    private MerchantAccountRepository merchantAccountRepository;

    @Autowired
    private MerchantApiKeyRecordRepository merchantApiKeyRecordRepository;

    private final List<Long> cleanupMerchants = new ArrayList<>();

    @AfterEach
    void tearDown() {
        cleanupMerchants.forEach(merchantId -> {
            merchantApiKeyRecordRepository.findAll().stream()
                    .filter(key -> key.getMerchantId().equals(merchantId))
                    .forEach(merchantApiKeyRecordRepository::delete);
            merchantAccountRepository.findById(merchantId).ifPresent(merchantAccountRepository::delete);
        });
        cleanupMerchants.clear();
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

        TrackingFilterChain adminProtectedChain = new TrackingFilterChain();
        ServerWebExchange adminProtectedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/admin/orders").build());

        apiKeyAuthFilter.filter(adminProtectedExchange, adminProtectedChain).block();
        assertTrue(adminProtectedChain.wasCalled());
    }

    @Test
    @DisplayName("OPTIONS requests bypass API key validation")
    void optionsRequestsAllowed() {
        TrackingFilterChain chain = new TrackingFilterChain();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/admin/auth/login").build());

        apiKeyAuthFilter.filter(exchange, chain).block();
        assertTrue(chain.wasCalled());
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
        String apiKey = "fxp_test_valid_key_123456";

        MerchantAccount merchant = MerchantAccount.builder()
                .email("merchant-valid@example.com")
                .status(MerchantStatus.ACTIVE)
                .build();
        merchant = merchantAccountRepository.save(merchant);
        cleanupMerchants.add(merchant.getId());

        MerchantApiKeyRecord apiKeyRecord = MerchantApiKeyRecord.builder()
                .merchantId(merchant.getId())
                .keyHash(sha256(apiKey))
                .active(true)
                .build();
        merchantApiKeyRecordRepository.save(apiKeyRecord);

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

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
