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
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@Slf4j
public class StripeProvider implements PaymentProvider {

    @Value("${payment.providers.stripe.secret-key}")
    private String secretKey;
    
    @Value("${payment.providers.stripe.webhook-secret}")
    private String webhookSecret;

    @Override
    @CircuitBreaker(name = "paymentService", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment with Stripe: {}", paymentRequest);
        
        // In a real implementation, this would call the Stripe API
        // For demo purposes, we'll simulate a successful payment
        
        return PaymentResponse.builder()
                .transactionId(paymentRequest.getOrderId()) // In real implementation, this would be the Stripe payment ID
                .orderId(paymentRequest.getOrderId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentChannel("STRIPE")
                .status(PaymentStatus.PROCESSING)
                .redirectUrl("https://stripe.com/checkout/session/123")
                .build();
    }

    public PaymentResponse processPaymentFallback(PaymentRequest paymentRequest, Throwable t) {
        log.error("Fallback for Stripe payment processing. Error: {}", t.getMessage());
        return PaymentResponse.builder()
                .transactionId(paymentRequest.getOrderId())
                .orderId(paymentRequest.getOrderId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentChannel("STRIPE")
                .status(PaymentStatus.FAILED)
                .errorMessage("Payment service is currently unavailable. Please try again later.")
                .build();
    }

    @Override
    public boolean validateCallback(String payload, String signature) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            String computedSignature = Base64.getEncoder().encodeToString(sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return computedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Error validating Stripe webhook signature", e);
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "STRIPE";
    }
}
