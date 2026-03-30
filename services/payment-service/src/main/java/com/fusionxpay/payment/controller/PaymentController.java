package com.fusionxpay.payment.controller;

import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentPageResponse;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.dto.RefundRequest;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment API", description = "APIs for payment processing")
public class PaymentController {

    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    private final PaymentService paymentService;

    @PostMapping("/request")
    @Operation(summary = "Initiate a payment", description = "Creates a new payment transaction and redirects to the payment provider")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader(HEADER_MERCHANT_ID) Long merchantId,
            @Valid @RequestBody PaymentRequest paymentRequest) {
        log.info("Payment request received: {}", paymentRequest);
        PaymentResponse response = paymentService.initiatePayment(merchantId, paymentRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get payment transaction", description = "Retrieves payment transaction details by transaction ID")
    public ResponseEntity<PaymentResponse> getPaymentTransaction(
            @RequestHeader(HEADER_MERCHANT_ID) Long merchantId,
            @PathVariable UUID transactionId) {
        log.info("Get payment transaction: {}", transactionId);
        PaymentResponse response = paymentService.getPaymentTransaction(merchantId, transactionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment by order", description = "Retrieves payment transaction details by order ID")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @RequestHeader(HEADER_MERCHANT_ID) Long merchantId,
            @PathVariable UUID orderId) {
        log.info("Get payment by order ID: {}", orderId);
        PaymentResponse response = paymentService.getPaymentTransactionByOrderId(merchantId, orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search payments", description = "Returns a paginated list of the authenticated merchant's payments")
    public ResponseEntity<PaymentPageResponse> searchPayments(
            @RequestHeader(HEADER_MERCHANT_ID) Long merchantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        PaymentPageResponse response = paymentService.searchPayments(merchantId, page, size, status, from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/providers")
    @Operation(summary = "Get available payment providers", description = "Returns a list of available payment providers")
    public ResponseEntity<List<String>> getAvailablePaymentProviders() {
        log.info("Get available payment providers");
        List<String> providers = paymentService.getAvailablePaymentProviders();
        return ResponseEntity.ok(providers);
    }

    @PostMapping("/refund")
    @Operation(summary = "Initiate a refund", description = "Processes a refund for an existing payment transaction")
    public ResponseEntity<RefundResponse> initiateRefund(
            @RequestHeader(HEADER_MERCHANT_ID) Long merchantId,
            @Valid @RequestBody RefundRequest refundRequest) {
        log.info("Refund request received for transaction: {}", refundRequest.getTransactionId());
        RefundResponse response = paymentService.initiateRefund(merchantId, refundRequest);
        return ResponseEntity.ok(response);
    }
}
