package com.fusionxpay.payment.provider;

import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.common.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * PayPal payment processor implementation
 */
@Service
@Slf4j
public class PayPalProvider implements PaymentProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${payment.providers.paypal.client-id}")
    private String clientId;
    
    @Value("${payment.providers.paypal.client-secret}")
    private String clientSecret;
    
    @Value("${payment.providers.paypal.webhook-id}")
    private String webhookId;

    @PostConstruct
    public void init() {
        log.info("PayPal payment provider initialized");
    }

    @Override
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment with PayPal: {}", paymentRequest);
        
        // In a real implementation, this would call the PayPal API
        // For demo purposes, we're simulating a processing payment
        String transactionId = UUID.randomUUID().toString();
        
        return PaymentResponse.builder()
                .transactionId(UUID.fromString(transactionId))
                .orderId(paymentRequest.getOrderId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentChannel(getProviderName())
                .status(PaymentStatus.PROCESSING)
                .redirectUrl("https://paypal.com/checkout/" + transactionId)
                .build();
    }

    public PaymentResponse processPaymentFallback(PaymentRequest paymentRequest, Throwable t) {
        log.error("Fallback for PayPal payment processing. Error: {}", t.getMessage());
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
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            String computedSignature = Base64.getEncoder().encodeToString(sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return computedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Error validating PayPal webhook signature: {}", e.getMessage(), e);
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
            // Parse PayPal callback data
            JsonNode rootNode = objectMapper.readTree(payload);
            String eventType = rootNode.path("event_type").asText();
            
            log.info("Processing PayPal webhook event: {}", eventType);
            
            switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED":
                case "CHECKOUT.ORDER.APPROVED":
                    return handlePaymentSuccess(rootNode);
                case "PAYMENT.CAPTURE.DENIED":
                case "PAYMENT.CAPTURE.DECLINED":
                    return handlePaymentFailure(rootNode);
                default:
                    log.info("Unhandled PayPal event type: {}", eventType);
                    return null; // Unsupported event, skip processing
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing PayPal webhook payload: {}", e.getMessage(), e);
            return createErrorResponse("Error parsing webhook payload");
        } catch (Exception e) {
            log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
            return createErrorResponse("Error processing webhook: " + e.getMessage());
        }
    }

    private PaymentResponse handlePaymentSuccess(JsonNode data) {
        try {
            // Extract transaction and order info from node
            String transactionId = data.path("resource").path("id").asText();
            // Custom data is typically stored in metadata or similar fields
            String orderId = data.path("resource").path("custom_id").asText();
            if (orderId == null || orderId.isEmpty()) {
                // Try to get from other possible locations
                orderId = data.path("resource").path("supplementary_data").path("related_ids").path("order_id").asText();
            }
            
            log.info("PayPal payment succeeded for transaction: {}, order: {}", transactionId, orderId);
            
            return PaymentResponse.builder()
                    .status(PaymentStatus.SUCCESS)
                    .transactionId(UUID.fromString(transactionId))
                    .orderId(UUID.fromString(orderId))
                    .paymentChannel(getProviderName())
                    .build();
        } catch (Exception e) {
            log.error("Error processing successful PayPal payment: {}", e.getMessage(), e);
            return createErrorResponse("Error processing successful payment: " + e.getMessage());
        }
    }

    private PaymentResponse handlePaymentFailure(JsonNode data) {
        try {
            String transactionId = data.path("resource").path("id").asText();
            String orderId = data.path("resource").path("custom_id").asText();
            String errorMessage = data.path("resource").path("status_details").path("reason").asText();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = "Payment failed";
            }
            
            log.error("PayPal payment failed: {}, transaction: {}, order: {}", 
                     errorMessage, transactionId, orderId);
            
            return PaymentResponse.builder()
                    .status(PaymentStatus.FAILED)
                    .transactionId(UUID.fromString(transactionId))
                    .orderId(UUID.fromString(orderId))
                    .paymentChannel(getProviderName())
                    .errorMessage(errorMessage)
                    .build();
        } catch (Exception e) {
            log.error("Error processing failed PayPal payment: {}", e.getMessage(), e);
            return createErrorResponse("Error processing failed payment: " + e.getMessage());
        }
    }

    private PaymentResponse createErrorResponse(String errorMessage) {
        return PaymentResponse.builder()
                .status(PaymentStatus.FAILED)
                .paymentChannel(getProviderName())
                .errorMessage(errorMessage)
                .build();
    }

    @Override
    public String getProviderName() {
        return "PAYPAL";
    }
}
