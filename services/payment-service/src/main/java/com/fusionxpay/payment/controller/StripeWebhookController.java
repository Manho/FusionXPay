package com.fusionxpay.payment.controller;

import com.fusionxpay.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/webhook/stripe")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stripe Webhook", description = "Stripe webhook endpoints")
public class StripeWebhookController {
    
    private final PaymentService paymentService;
    
    @PostMapping
    @Operation(summary = "Handle Stripe webhook", description = "Processes webhook notifications from Stripe")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        
        log.info("Received Stripe webhook");
        boolean processed = paymentService.handleCallback(payload, signature, "STRIPE");
        
        if (processed) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }
}