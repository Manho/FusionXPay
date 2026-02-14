package com.fusionxpay.payment.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.dto.paypal.*;
import com.fusionxpay.payment.service.IdempotencyService;
import com.fusionxpay.payment.service.PayPalAuthService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * PayPal payment provider implementation.
 * Integrates with PayPal Orders API v2 for payment processing.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class PayPalProvider implements PaymentProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final PayPalAuthService payPalAuthService;
    private final IdempotencyService idempotencyService;

    @Value("${payment.providers.paypal.webhook-id}")
    private String webhookId;

    @Value("${payment.providers.paypal.return-url}")
    private String returnUrl;

    @Value("${payment.providers.paypal.cancel-url}")
    private String cancelUrl;

    public PayPalProvider(PayPalAuthService payPalAuthService, IdempotencyService idempotencyService) {
        this.payPalAuthService = payPalAuthService;
        this.idempotencyService = idempotencyService;
    }

    @PostConstruct
    public void init() {
        log.info("PayPal payment provider initialized");
    }

    @Override
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment with PayPal for order: {}", paymentRequest.getOrderId());

        try {
            // Get access token
            String accessToken = payPalAuthService.getAccessToken();
            String baseUrl = payPalAuthService.getBaseUrl();

            // Build PayPal order request
            PayPalOrderRequest orderRequest = buildOrderRequest(paymentRequest);

            // Build HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            headers.set("PayPal-Request-Id", paymentRequest.getOrderId().toString()); // Idempotency key

            HttpEntity<PayPalOrderRequest> request = new HttpEntity<>(orderRequest, headers);

            // Call PayPal Orders API
            ResponseEntity<PayPalOrderResponse> responseEntity = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders",
                    HttpMethod.POST,
                    request,
                    PayPalOrderResponse.class
            );

            PayPalOrderResponse response = responseEntity.getBody();
            if (response == null || response.getId() == null) {
                log.error("PayPal returned empty response for order: {}", paymentRequest.getOrderId());
                return createErrorResponse("PayPal returned empty response");
            }

            // Extract approval URL from links
            String approveUrl = extractApproveUrl(response.getLinks());
            if (approveUrl == null) {
                log.error("No approval URL found in PayPal response for order: {}", paymentRequest.getOrderId());
                return createErrorResponse("No approval URL found in PayPal response");
            }

            log.info("PayPal order created successfully. OrderId: {}, PayPalOrderId: {}",
                    paymentRequest.getOrderId(), response.getId());

            return PaymentResponse.builder()
                    .transactionId(UUID.randomUUID())
                    .orderId(paymentRequest.getOrderId())
                    .amount(paymentRequest.getAmount())
                    .currency(paymentRequest.getCurrency())
                    .paymentChannel(getProviderName())
                    .providerTransactionId(response.getId())
                    .status(PaymentStatus.PROCESSING)
                    .redirectUrl(approveUrl)
                    .build();

        } catch (RestClientException e) {
            log.error("PayPal API call failed for order {}: {}", paymentRequest.getOrderId(), e.getMessage(), e);
            return createErrorResponse("PayPal API call failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing PayPal payment for order {}: {}",
                    paymentRequest.getOrderId(), e.getMessage(), e);
            return createErrorResponse("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Fallback method for circuit breaker.
     */
    public PaymentResponse processPaymentFallback(PaymentRequest paymentRequest, Throwable t) {
        log.error("Fallback for PayPal payment processing. Order: {}, Error: {}",
                paymentRequest.getOrderId(), t.getMessage());
        return PaymentResponse.builder()
                .transactionId(paymentRequest.getOrderId())
                .orderId(paymentRequest.getOrderId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentChannel(getProviderName())
                .status(PaymentStatus.FAILED)
                .errorMessage("Payment service is currently unavailable. Please try again later.")
                .build();
    }

    @Override
    public boolean validateCallback(String payload, String signature) {
        // Parse the signature parameter as JSON containing webhook headers
        // The signature parameter contains serialized PayPalWebhookHeaders
        if (signature == null || signature.isEmpty()) {
            log.error("PayPal webhook validation failed: missing signature/headers");
            return false;
        }

        try {
            // First, validate the payload structure
            JsonNode event = objectMapper.readTree(payload);
            String eventType = event.path("event_type").asText();
            String eventId = event.path("id").asText();

            if (eventType.isEmpty() || eventId.isEmpty()) {
                log.error("Invalid PayPal webhook: missing event_type or id");
                return false;
            }

            // Parse the webhook headers from signature parameter
            PayPalWebhookHeaders headers = objectMapper.readValue(signature, PayPalWebhookHeaders.class);

            // Call PayPal's webhook verification API
            boolean isValid = payPalAuthService.verifyWebhookSignature(headers, webhookId, payload);

            if (isValid) {
                log.info("PayPal webhook signature verified. EventId: {}, EventType: {}", eventId, eventType);
            } else {
                log.error("PayPal webhook signature verification failed for event: {}", eventId);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error validating PayPal webhook: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public PaymentResponse processCallback(String payload, String signature) {
        log.info("Processing PayPal webhook callback");

        if (!validateCallback(payload, signature)) {
            log.error("Invalid webhook signature from PayPal");
            return createErrorResponse("Invalid webhook signature");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            String eventId = rootNode.path("id").asText();
            String eventType = rootNode.path("event_type").asText();

            log.info("Processing PayPal webhook event: {} ({})", eventType, eventId);

            // Idempotency check - prevent duplicate processing
            String eventKey = "paypal:webhook:event:" + eventId;
            if (!idempotencyService.acquireProcessingLock(eventKey, Duration.ofDays(7))) {
                log.info("Duplicate PayPal webhook event: {}", eventId);
                return PaymentResponse.builder()
                        .status(PaymentStatus.DUPLICATE)
                        .paymentChannel(getProviderName())
                        .errorMessage("Event already processed")
                        .build();
            }

            try {
                switch (eventType) {
                    case "PAYMENT.CAPTURE.COMPLETED":
                        return handlePaymentCaptureCompleted(rootNode);
                    case "CHECKOUT.ORDER.APPROVED":
                        return handleOrderApproved(rootNode);
                    case "PAYMENT.CAPTURE.DENIED":
                    case "PAYMENT.CAPTURE.DECLINED":
                        return handlePaymentFailure(rootNode);
                    case "PAYMENT.CAPTURE.REFUNDED":
                        return handleRefundCompleted(rootNode);
                    default:
                        log.info("Unhandled PayPal event type: {}", eventType);
                        return null;
                }
            } finally {
                idempotencyService.releaseLock(eventKey);
            }

        } catch (JsonProcessingException e) {
            log.error("Error parsing PayPal webhook payload: {}", e.getMessage(), e);
            return createErrorResponse("Error parsing webhook payload");
        } catch (Exception e) {
            log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
            return createErrorResponse("Error processing webhook: " + e.getMessage());
        }
    }

    /**
     * Captures an approved PayPal order.
     *
     * @param paypalOrderId the PayPal order ID
     * @return capture response
     */
    public PayPalOrderResponse captureOrder(String paypalOrderId) {
        log.info("Capturing PayPal order: {}", paypalOrderId);

        try {
            String accessToken = payPalAuthService.getAccessToken();
            String baseUrl = payPalAuthService.getBaseUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<String> request = new HttpEntity<>("{}", headers);

            ResponseEntity<PayPalOrderResponse> responseEntity = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                    HttpMethod.POST,
                    request,
                    PayPalOrderResponse.class
            );

            PayPalOrderResponse response = responseEntity.getBody();
            log.info("PayPal order captured successfully. OrderId: {}, Status: {}",
                    paypalOrderId, response != null ? response.getStatus() : "unknown");

            return response;

        } catch (HttpStatusCodeException e) {
            // PayPal returns 422 ORDER_ALREADY_CAPTURED when the return URL is hit multiple times
            // (browser refresh, duplicate redirects, retries). Treat it as idempotent by fetching
            // the order and continuing if it is already COMPLETED.
            String body = e.getResponseBodyAsString();
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY
                    && body != null
                    && body.contains("ORDER_ALREADY_CAPTURED")) {
                log.warn("PayPal order {} already captured (422). Fetching order details.", paypalOrderId);
                PayPalOrderResponse existing = getOrder(paypalOrderId);
                if (existing != null) {
                    log.info("Fetched PayPal order {}. Status: {}", paypalOrderId, existing.getStatus());
                    return existing;
                }
            }

            log.error("Error capturing PayPal order {}. Status: {}, Body: {}",
                    paypalOrderId, e.getStatusCode(), body);
            throw new RuntimeException("Failed to capture PayPal order: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error capturing PayPal order {}: {}", paypalOrderId, e.getMessage(), e);
            throw new RuntimeException("Failed to capture PayPal order: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a PayPal order by ID.
     *
     * @param paypalOrderId the PayPal order ID
     * @return order details
     */
    public PayPalOrderResponse getOrder(String paypalOrderId) {
        try {
            String accessToken = payPalAuthService.getAccessToken();
            String baseUrl = payPalAuthService.getBaseUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<PayPalOrderResponse> responseEntity = restTemplate.exchange(
                    baseUrl + "/v2/checkout/orders/" + paypalOrderId,
                    HttpMethod.GET,
                    request,
                    PayPalOrderResponse.class
            );

            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("Error fetching PayPal order {}: {}", paypalOrderId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Processes a refund for a captured PayPal payment.
     *
     * @param captureId the PayPal capture ID
     * @param amount the amount to refund (null for full refund)
     * @param currency the currency code
     * @param reason the reason for refund
     * @return refund response
     */
    public PayPalRefundResponse processRefund(String captureId, BigDecimal amount, String currency, String reason) {
        log.info("Processing PayPal refund for capture: {}", captureId);

        try {
            String accessToken = payPalAuthService.getAccessToken();
            String baseUrl = payPalAuthService.getBaseUrl();

            // Build refund request
            PayPalRefundRequest.PayPalRefundRequestBuilder refundBuilder = PayPalRefundRequest.builder();

            // If amount is specified, it's a partial refund
            if (amount != null && currency != null) {
                refundBuilder.amount(Amount.builder()
                        .currencyCode(currency)
                        .value(amount.toPlainString())
                        .build());
            }

            if (reason != null && !reason.isEmpty()) {
                refundBuilder.noteToPayer(reason);
            }

            PayPalRefundRequest refundRequest = refundBuilder.build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            HttpEntity<PayPalRefundRequest> request = new HttpEntity<>(refundRequest, headers);

            ResponseEntity<PayPalRefundResponse> responseEntity = restTemplate.exchange(
                    baseUrl + "/v2/payments/captures/" + captureId + "/refund",
                    HttpMethod.POST,
                    request,
                    PayPalRefundResponse.class
            );

            PayPalRefundResponse response = responseEntity.getBody();
            log.info("PayPal refund processed. CaptureId: {}, RefundId: {}, Status: {}",
                    captureId,
                    response != null ? response.getId() : "unknown",
                    response != null ? response.getStatus() : "unknown");

            return response;

        } catch (Exception e) {
            log.error("Error processing PayPal refund for capture {}: {}", captureId, e.getMessage(), e);
            throw new RuntimeException("Failed to process PayPal refund: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "PAYPAL";
    }

    // ========== Private Helper Methods ==========

    private PayPalOrderRequest buildOrderRequest(PaymentRequest paymentRequest) {
        String orderId = paymentRequest.getOrderId().toString();

        PurchaseUnit purchaseUnit = PurchaseUnit.builder()
                .referenceId(orderId)
                .customId(orderId)
                .amount(Amount.builder()
                        .currencyCode(paymentRequest.getCurrency())
                        .value(paymentRequest.getAmount().toPlainString())
                        .build())
                .description("Payment for Order " + orderId)
                .build();

        ApplicationContext appContext = ApplicationContext.builder()
                .brandName("FusionXPay")
                .returnUrl(returnUrl + "?orderId=" + orderId)
                .cancelUrl(cancelUrl + "?orderId=" + orderId)
                .userAction("PAY_NOW")
                .shippingPreference("NO_SHIPPING")
                .build();

        return PayPalOrderRequest.builder()
                .intent("CAPTURE")
                .purchaseUnits(List.of(purchaseUnit))
                .applicationContext(appContext)
                .build();
    }

    private String extractApproveUrl(List<Link> links) {
        if (links == null) {
            return null;
        }
        return links.stream()
                .filter(link -> "approve".equals(link.getRel()))
                .findFirst()
                .map(Link::getHref)
                .orElse(null);
    }

    private PaymentResponse handlePaymentCaptureCompleted(JsonNode data) {
        try {
            JsonNode resource = data.path("resource");
            String captureId = resource.path("id").asText();
            String customId = resource.path("custom_id").asText();

            // Try to get orderId from supplementary data if not in custom_id
            if (customId.isEmpty()) {
                customId = resource.path("supplementary_data")
                        .path("related_ids")
                        .path("order_id").asText();
            }

            log.info("PayPal payment capture completed. CaptureId: {}, OrderId: {}", captureId, customId);

            return PaymentResponse.builder()
                    .status(PaymentStatus.SUCCESS)
                    .providerTransactionId(captureId)
                    .orderId(parseUUID(customId))
                    .paymentChannel(getProviderName())
                    .build();

        } catch (Exception e) {
            log.error("Error processing payment capture completed: {}", e.getMessage(), e);
            return createErrorResponse("Error processing payment completion: " + e.getMessage());
        }
    }

    private PaymentResponse handleOrderApproved(JsonNode data) {
        try {
            JsonNode resource = data.path("resource");
            String paypalOrderId = resource.path("id").asText();

            // Get customId from purchase_units
            String customId = resource.path("purchase_units")
                    .path(0)
                    .path("custom_id").asText();

            log.info("PayPal order approved. PayPalOrderId: {}, OrderId: {}", paypalOrderId, customId);

            // Auto-capture the order
            PayPalOrderResponse captureResponse = captureOrder(paypalOrderId);

            if (captureResponse != null && "COMPLETED".equals(captureResponse.getStatus())) {
                String captureId = extractFirstCaptureId(captureResponse);
                return PaymentResponse.builder()
                        .status(PaymentStatus.SUCCESS)
                        // Persist capture id for refunds when available; fall back to order id.
                        .providerTransactionId(captureId != null ? captureId : paypalOrderId)
                        .orderId(parseUUID(customId))
                        .paymentChannel(getProviderName())
                        .build();
            } else {
                return PaymentResponse.builder()
                        .status(PaymentStatus.PROCESSING)
                        .providerTransactionId(paypalOrderId)
                        .orderId(parseUUID(customId))
                        .paymentChannel(getProviderName())
                        .build();
            }

        } catch (Exception e) {
            log.error("Error processing order approved: {}", e.getMessage(), e);
            return createErrorResponse("Error processing order approval: " + e.getMessage());
        }
    }

    private PaymentResponse handlePaymentFailure(JsonNode data) {
        try {
            JsonNode resource = data.path("resource");
            String captureId = resource.path("id").asText();
            String customId = resource.path("custom_id").asText();
            String errorReason = resource.path("status_details").path("reason").asText();

            if (errorReason.isEmpty()) {
                errorReason = "Payment declined";
            }

            log.error("PayPal payment failed. CaptureId: {}, OrderId: {}, Reason: {}",
                    captureId, customId, errorReason);

            return PaymentResponse.builder()
                    .status(PaymentStatus.FAILED)
                    .providerTransactionId(captureId)
                    .orderId(parseUUID(customId))
                    .paymentChannel(getProviderName())
                    .errorMessage(errorReason)
                    .build();

        } catch (Exception e) {
            log.error("Error processing payment failure: {}", e.getMessage(), e);
            return createErrorResponse("Error processing payment failure: " + e.getMessage());
        }
    }

    private PaymentResponse handleRefundCompleted(JsonNode data) {
        try {
            JsonNode resource = data.path("resource");
            String refundId = resource.path("id").asText();
            String captureId = resource.path("links")
                    .path(0)
                    .path("href").asText();

            log.info("PayPal refund completed. RefundId: {}", refundId);

            return PaymentResponse.builder()
                    .status(PaymentStatus.SUCCESS)
                    .providerTransactionId(refundId)
                    .paymentChannel(getProviderName())
                    .errorMessage("Refund completed")
                    .build();

        } catch (Exception e) {
            log.error("Error processing refund completed: {}", e.getMessage(), e);
            return createErrorResponse("Error processing refund: " + e.getMessage());
        }
    }

    private UUID parseUUID(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: {}", value);
            return null;
        }
    }

    private PaymentResponse createErrorResponse(String errorMessage) {
        return PaymentResponse.builder()
                .status(PaymentStatus.FAILED)
                .paymentChannel(getProviderName())
                .errorMessage(errorMessage)
                .build();
    }

    private String extractFirstCaptureId(PayPalOrderResponse orderResponse) {
        try {
            if (orderResponse.getPurchaseUnits() == null || orderResponse.getPurchaseUnits().isEmpty()) {
                return null;
            }
            var payments = orderResponse.getPurchaseUnits().get(0).getPayments();
            if (payments == null || payments.getCaptures() == null || payments.getCaptures().isEmpty()) {
                return null;
            }
            return payments.getCaptures().get(0).getId();
        } catch (Exception ignored) {
            return null;
        }
    }
}
