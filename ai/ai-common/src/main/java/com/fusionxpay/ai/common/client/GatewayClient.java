package com.fusionxpay.ai.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginRequest;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import com.fusionxpay.ai.common.dto.order.OrderRecord;
import com.fusionxpay.ai.common.dto.order.OrderSearchRequest;
import com.fusionxpay.ai.common.dto.payment.InitiatePaymentRequest;
import com.fusionxpay.ai.common.dto.payment.PaymentPageResult;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.dto.payment.PaymentSearchRequest;
import com.fusionxpay.ai.common.dto.payment.RefundPaymentRequest;
import com.fusionxpay.ai.common.dto.payment.RefundResult;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import com.fusionxpay.ai.common.exception.AiGatewayException;
import com.fusionxpay.ai.common.exception.AiTransportException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RequiredArgsConstructor
public class GatewayClient {

    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GatewayLoginResponse login(String email, String password) {
        return execute(restClient.post()
                .uri("/api/v1/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GatewayLoginRequest(email, password)), GatewayLoginResponse.class);
    }

    public GatewayMerchantInfo getCurrentMerchant(String token) {
        return execute(authenticated(restClient.get().uri("/api/v1/admin/auth/me"), token, null), GatewayMerchantInfo.class);
    }

    public OrderRecord getOrderByNumber(String token, Long merchantId, String orderNumber) {
        return execute(authenticated(restClient.get().uri("/api/v1/orders/{orderNumber}", orderNumber), token, merchantId), OrderRecord.class);
    }

    public OrderRecord getOrderById(String token, Long merchantId, UUID orderId) {
        return execute(authenticated(restClient.get().uri("/api/v1/orders/id/{orderId}", orderId), token, merchantId), OrderRecord.class);
    }

    public OrderPageResult searchOrders(String token, Long merchantId, OrderSearchRequest request) {
        String uri = UriComponentsBuilder.fromPath("/api/v1/orders")
                .queryParam("page", request.getPage())
                .queryParam("size", request.getSize())
                .queryParamIfPresent("status", nullable(request.getStatus()))
                .queryParamIfPresent("orderNumber", nullable(request.getOrderNumber()))
                .queryParamIfPresent("from", nullable(request.getFrom()))
                .queryParamIfPresent("to", nullable(request.getTo()))
                .build()
                .toUriString();
        return execute(authenticated(restClient.get().uri(uri), token, merchantId), OrderPageResult.class);
    }

    public PaymentRecord queryPayment(String token, Long merchantId, UUID transactionId) {
        return execute(authenticated(restClient.get().uri("/api/v1/payment/transaction/{transactionId}", transactionId), token, merchantId), PaymentRecord.class);
    }

    public PaymentRecord queryPaymentByOrder(String token, Long merchantId, UUID orderId) {
        return execute(authenticated(restClient.get().uri("/api/v1/payment/order/{orderId}", orderId), token, merchantId), PaymentRecord.class);
    }

    public PaymentPageResult searchPayments(String token, Long merchantId, PaymentSearchRequest request) {
        String uri = UriComponentsBuilder.fromPath("/api/v1/payment/search")
                .queryParam("page", request.getPage())
                .queryParam("size", request.getSize())
                .queryParamIfPresent("status", nullable(request.getStatus()))
                .queryParamIfPresent("from", nullable(request.getFrom()))
                .queryParamIfPresent("to", nullable(request.getTo()))
                .build()
                .toUriString();
        return execute(authenticated(restClient.get().uri(uri), token, merchantId), PaymentPageResult.class);
    }

    public PaymentRecord initiatePayment(String token, Long merchantId, InitiatePaymentRequest request) {
        return execute(authenticated(restClient.post()
                .uri("/api/v1/payment/request")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request), token, merchantId), PaymentRecord.class);
    }

    public RefundResult refundPayment(String token, Long merchantId, RefundPaymentRequest request) {
        return execute(authenticated(restClient.post()
                .uri("/api/v1/payment/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request), token, merchantId), RefundResult.class);
    }

    private RestClient.RequestHeadersSpec<?> authenticated(RestClient.RequestHeadersSpec<?> spec,
                                                           String token,
                                                           @Nullable Long merchantId) {
        return spec.headers(headers -> {
            headers.setBearerAuth(token);
            if (merchantId != null) {
                headers.add(HEADER_MERCHANT_ID, String.valueOf(merchantId));
            }
        });
    }

    private <T> T execute(RestClient.RequestHeadersSpec<?> spec, Class<T> responseType) {
        try {
            return spec.retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw mapGatewayException(ex);
        } catch (ResourceAccessException ex) {
            throw new AiTransportException("Gateway request failed", ex);
        }
    }

    private RuntimeException mapGatewayException(RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        String message = extractMessage(responseBody);
        if (message == null || message.isBlank()) {
            message = "Gateway request failed with status " + ex.getStatusCode().value();
        }

        int statusCode = ex.getStatusCode().value();
        if (statusCode == 401 || statusCode == 403) {
            return new AiAuthenticationException(statusCode, message, responseBody, ex);
        }
        return new AiGatewayException(statusCode, message, responseBody, ex);
    }

    private String extractMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
            if (root.hasNonNull("errorMessage")) {
                return root.get("errorMessage").asText();
            }
            if (root.hasNonNull("error")) {
                return root.get("error").asText();
            }
        } catch (Exception ignored) {
            // Fall through and return raw body for non-JSON errors.
        }
        return responseBody;
    }

    private static java.util.Optional<String> nullable(String value) {
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }
}
