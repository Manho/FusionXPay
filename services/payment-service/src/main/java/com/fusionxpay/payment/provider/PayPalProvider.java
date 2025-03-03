package com.fusionxpay.payment.provider;

import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class PayPalProvider implements PaymentProvider {

    @Value("${payment.providers.paypal.client-id}")
    private String clientId;
    
    @Value("${payment.providers.paypal.client-secret}")
    private String clientSecret;
    
    @Value("${payment.providers.paypal.webhook-id}")
    private String webhookId;

    @Override
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment with PayPal: {}", paymentRequest);
        
        // In a real implementation, this would call the PayPal API
        // For demo purposes, we'll simulate a successful payment
        
        return PaymentResponse.builder()
                .transactionId(paymentRequest.getOrderId()) // In real implementation, this would be the PayPal payment ID
                .orderId(paymentRequest.getOrderId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentChannel("PAYPAL")
                .status(PaymentStatus.PROCESSING)
                .redirectUrl("https://paypal.com/checkout/123")
                .build();
    }

    public PaymentResponse processPaymentFallback(PaymentRequest paymentRequest, Throwable t) {
        log.error("Fallback for PayPal payment processing. Error: {}", t.getMessage());
        return PaymentResponse.builder()
                .transactionId(paymentRequest.getOrderId())
                .orderId(paymentRequest.getOrderId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentChannel("PAYPAL")
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
            log.error("Error validating PayPal webhook signature", e);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "PAYPAL";
    }
}
