package com.fusionxpay.payment.controller;

import com.fusionxpay.payment.dto.PaymentCallbackRequest;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment API", description = "APIs for payment processing")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/request")
    @Operation(summary = "Initiate a payment", description = "Creates a new payment transaction and redirects to the payment provider")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        log.info("Payment request received: {}", paymentRequest);
        PaymentResponse response = paymentService.initiatePayment(paymentRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/callback")
    @Operation(summary = "Payment callback", description = "Endpoint for payment providers to send payment status updates")
    public ResponseEntity<Void> paymentCallback(@RequestBody PaymentCallbackRequest callbackRequest) {
        log.info("Payment callback received: {}", callbackRequest);
        boolean processed = paymentService.processPaymentCallback(callbackRequest);
        
        if (processed) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get payment transaction", description = "Retrieves payment transaction details by transaction ID")
    public ResponseEntity<PaymentResponse> getPaymentTransaction(@PathVariable UUID transactionId) {
        log.info("Get payment transaction: {}", transactionId);
        PaymentResponse response = paymentService.getPaymentTransaction(transactionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment by order", description = "Retrieves payment transaction details by order ID")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable UUID orderId) {
        log.info("Get payment by order ID: {}", orderId);
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/providers")
    @Operation(summary = "Get available payment providers", description = "Returns a list of available payment providers")
    public ResponseEntity<List<String>> getAvailablePaymentProviders() {
        log.info("Get available payment providers");
        List<String> providers = paymentService.getAvailablePaymentProviders();
        return ResponseEntity.ok(providers);
    }
}