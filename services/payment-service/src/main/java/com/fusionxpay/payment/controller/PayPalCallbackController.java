package com.fusionxpay.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.payment.dto.paypal.PayPalOrderResponse;
import com.fusionxpay.payment.dto.paypal.PayPalWebhookHeaders;
import com.fusionxpay.payment.provider.PayPalProvider;
import com.fusionxpay.payment.service.IdempotencyService;
import com.fusionxpay.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Duration;
import java.util.UUID;

/**
 * Controller for handling PayPal payment callbacks.
 * Processes return and cancel URLs from PayPal checkout flow.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/payment/paypal")
@RequiredArgsConstructor
@Slf4j
public class PayPalCallbackController {

    private final PayPalProvider payPalProvider;
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @Value("${payment.frontend.success-url:http://localhost:3000/payment/success}")
    private String frontendSuccessUrl;

    @Value("${payment.frontend.cancel-url:http://localhost:3000/payment/cancel}")
    private String frontendCancelUrl;

    @Value("${payment.frontend.error-url:http://localhost:3000/payment/error}")
    private String frontendErrorUrl;

    /**
     * Handles the return callback from PayPal after user approves payment.
     * Captures the payment and redirects to frontend success page.
     *
     * @param token PayPal order token (same as order ID)
     * @param payerId PayPal payer ID
     * @param orderId internal order ID from query param
     * @return redirect to frontend success or error page
     */
    @GetMapping("/return")
    public RedirectView handleReturn(
            @RequestParam(required = false) String token,
            @RequestParam(value = "PayerID", required = false) String payerId,
            @RequestParam(required = false) String orderId) {

        log.info("PayPal return callback received. Token: {}, PayerID: {}, OrderId: {}",
                token, payerId, orderId);

        try {
            // Token is the PayPal order ID
            String paypalOrderId = token;

            if (paypalOrderId == null || paypalOrderId.isEmpty()) {
                log.error("Missing PayPal order token in return callback");
                return new RedirectView(frontendErrorUrl + "?error=missing_token");
            }

            // Idempotency: PayPal/browser may trigger return multiple times (refresh/retry).
            // We must ensure we don't repeatedly call capture and produce 422 in PayPal logs.
            String captureKey = "paypal:return:capture:" + paypalOrderId;
            String existingState = idempotencyService.getProcessingState(captureKey);
            if (existingState != null) {
                log.warn("Skipping PayPal capture for {} due to idempotency state={}", paypalOrderId, existingState);
                return new RedirectView(frontendSuccessUrl +
                        "?orderId=" + orderId +
                        "&paypalOrderId=" + paypalOrderId);
            }

            boolean lockAcquired = idempotencyService.acquireProcessingLock(captureKey, Duration.ofMinutes(5));
            if (!lockAcquired) {
                log.warn("Skipping PayPal capture for {} because lock not acquired", paypalOrderId);
                return new RedirectView(frontendSuccessUrl +
                        "?orderId=" + orderId +
                        "&paypalOrderId=" + paypalOrderId);
            }

            // Avoid duplicate capture calls (browser refresh / retries). If we already stored a capture ID
            // (providerTransactionId changed from PayPal order ID -> capture ID), skip capture.
            if (orderId != null && !orderId.isBlank()) {
                try {
                    UUID orderUuid = UUID.fromString(orderId);
                    var txOpt = paymentService.findTransactionByOrderId(orderUuid);
                    if (txOpt.isPresent()) {
                        String st = txOpt.get().getStatus();
                        String providerTx = txOpt.get().getProviderTransactionId();
                        if (com.fusionxpay.common.model.PaymentStatus.SUCCESS.name().equals(st)
                                || com.fusionxpay.common.model.PaymentStatus.REFUNDED.name().equals(st)
                                || (providerTx != null && !providerTx.isBlank() && !providerTx.equals(paypalOrderId))) {
                            log.warn("Skipping PayPal capture for order {} (status={}, providerTx={})", orderId, st, providerTx);
                            return new RedirectView(frontendSuccessUrl +
                                    "?orderId=" + orderId +
                                    "&paypalOrderId=" + paypalOrderId);
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore parse/lookup errors and proceed with capture.
                }
            }

            // Capture the payment
            PayPalOrderResponse captureResponse = payPalProvider.captureOrder(paypalOrderId);

            if (captureResponse == null) {
                log.error("Failed to capture PayPal order: {}", paypalOrderId);
                idempotencyService.releaseLock(captureKey);
                return new RedirectView(frontendErrorUrl + "?error=capture_failed&orderId=" + orderId);
            }

            String status = captureResponse.getStatus();
            log.info("PayPal order captured. OrderId: {}, PayPalOrderId: {}, Status: {}",
                    orderId, paypalOrderId, status);

            if ("COMPLETED".equals(status)) {
                // Persist capture ID for refunds. Payment SUCCESS is confirmed by PayPal webhook
                // (PAYMENT.CAPTURE.COMPLETED) rather than this browser return redirect.
                if (orderId != null && !orderId.isEmpty()) {
                    try {
                        // Persist capture id for refunds when available.
                        String captureId = null;
                        try {
                            if (captureResponse.getPurchaseUnits() != null && !captureResponse.getPurchaseUnits().isEmpty()
                                    && captureResponse.getPurchaseUnits().get(0).getPayments() != null
                                    && captureResponse.getPurchaseUnits().get(0).getPayments().getCaptures() != null
                                    && !captureResponse.getPurchaseUnits().get(0).getPayments().getCaptures().isEmpty()) {
                                captureId = captureResponse.getPurchaseUnits().get(0).getPayments().getCaptures().get(0).getId();
                            }
                        } catch (Exception ignored) {
                            captureId = null;
                        }

                        UUID orderUuid = UUID.fromString(orderId);
                        if (captureId != null && !captureId.isBlank()) {
                            paymentService.updateProviderTransactionId(orderUuid, captureId);
                            log.info("Stored PayPal captureId for orderId={}", orderId);
                        } else {
                            log.warn("PayPal captureId missing in capture response for orderId={}", orderId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to update payment status for order {}: {}",
                                orderId, e.getMessage(), e);
                    }
                }

                idempotencyService.markAsCompleted(captureKey, Duration.ofDays(7));
                return new RedirectView(frontendSuccessUrl +
                        "?orderId=" + orderId +
                        "&paypalOrderId=" + paypalOrderId);
            } else {
                log.warn("PayPal order not completed. Status: {}", status);
                idempotencyService.releaseLock(captureKey);
                return new RedirectView(frontendErrorUrl +
                        "?error=payment_incomplete&status=" + status +
                        "&orderId=" + orderId);
            }

        } catch (Exception e) {
            log.error("Error processing PayPal return callback: {}", e.getMessage(), e);
            return new RedirectView(frontendErrorUrl +
                    "?error=processing_error&orderId=" + orderId);
        }
    }

    /**
     * Handles the cancel callback from PayPal when user cancels payment.
     *
     * @param orderId internal order ID
     * @return redirect to frontend cancel page
     */
    @GetMapping("/cancel")
    public RedirectView handleCancel(@RequestParam(required = false) String orderId) {
        log.info("PayPal cancel callback received. OrderId: {}", orderId);

        // Update internal order status to cancelled/failed
        if (orderId != null && !orderId.isEmpty()) {
            try {
                paymentService.updatePaymentStatus(
                        UUID.fromString(orderId),
                        com.fusionxpay.common.model.PaymentStatus.FAILED
                );
            } catch (Exception e) {
                log.error("Failed to update cancelled payment status for order {}: {}",
                        orderId, e.getMessage(), e);
            }
        }

        return new RedirectView(frontendCancelUrl + "?orderId=" + orderId);
    }

    /**
     * Handles PayPal webhook notifications.
     * This endpoint receives asynchronous updates from PayPal.
     *
     * @param payload webhook payload
     * @param authAlgo PAYPAL-AUTH-ALGO header
     * @param certUrl PAYPAL-CERT-URL header
     * @param transmissionId PAYPAL-TRANSMISSION-ID header
     * @param transmissionSig PAYPAL-TRANSMISSION-SIG header
     * @param transmissionTime PAYPAL-TRANSMISSION-TIME header
     * @return acknowledgement response
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime) {

        log.info("PayPal webhook received. TransmissionId: {}", transmissionId);

        try {
            // Build webhook headers object
            PayPalWebhookHeaders headers = PayPalWebhookHeaders.builder()
                    .authAlgo(authAlgo)
                    .certUrl(certUrl)
                    .transmissionId(transmissionId)
                    .transmissionSig(transmissionSig)
                    .transmissionTime(transmissionTime)
                    .build();

            // Serialize headers to JSON for passing to provider
            ObjectMapper objectMapper = new ObjectMapper();
            String headersJson = objectMapper.writeValueAsString(headers);

            boolean processed = paymentService.handleCallback(payload, headersJson, "PAYPAL");
            if (processed) {
                return ResponseEntity.ok("Webhook processed successfully");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook rejected");

        } catch (Exception e) {
            log.error("Error processing PayPal webhook: {}", e.getMessage(), e);
            // Return 400 so PayPal retries; we should not silently drop provider notifications.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook processing error");
        }
    }

    /**
     * Health check endpoint for PayPal integration.
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("PayPal integration is healthy");
    }
}
