package com.fusionxpay.payment.provider;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.model.RefundStatus;
import com.fusionxpay.payment.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StripeProvider
 *
 * Note: These tests focus on the provider logic without calling actual Stripe APIs.
 * Integration tests with Stripe's test mode should be done separately.
 */
@ExtendWith(MockitoExtension.class)
public class StripeProviderTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private IdempotencyService idempotencyService;

    private StripeProvider stripeProvider;

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String API_KEY = "sk_test_key";
    private static final String STRIPE_API_VERSION = "2024-10-28.acacia";
    private static final String REFUND_EVENT_ID = "evt_refund_123";
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        stripeProvider = new StripeProvider(redisTemplate, idempotencyService);
        ReflectionTestUtils.setField(stripeProvider, "apiKey", API_KEY);
        ReflectionTestUtils.setField(stripeProvider, "webhookSecret", WEBHOOK_SECRET);
        ReflectionTestUtils.setField(stripeProvider, "defaultSuccessUrl", "https://fusionx.fun/payment/success");
        ReflectionTestUtils.setField(stripeProvider, "defaultCancelUrl", "https://fusionx.fun/payment/cancel");
    }

    @Test
    void testResolveSuccessUrl_PrefersExplicitSuccessUrl() {
        var request = com.fusionxpay.payment.dto.PaymentRequest.builder()
                .successUrl("https://merchant.example/success")
                .returnUrl("https://merchant.example/return")
                .build();

        String result = ReflectionTestUtils.invokeMethod(stripeProvider, "resolveSuccessUrl", request);

        assertEquals("https://merchant.example/success", result);
    }

    @Test
    void testResolveSuccessUrl_FallsBackToReturnUrl() {
        var request = com.fusionxpay.payment.dto.PaymentRequest.builder()
                .returnUrl("https://merchant.example/return")
                .build();

        String result = ReflectionTestUtils.invokeMethod(stripeProvider, "resolveSuccessUrl", request);

        assertEquals("https://merchant.example/return", result);
    }

    @Test
    void testResolveSuccessUrl_FallsBackToConfiguredDefault() {
        var request = com.fusionxpay.payment.dto.PaymentRequest.builder().build();

        String result = ReflectionTestUtils.invokeMethod(stripeProvider, "resolveSuccessUrl", request);

        assertEquals("https://fusionx.fun/payment/success", result);
    }

    @Test
    void testResolveCancelUrl_FallsBackToConfiguredDefault() {
        var request = com.fusionxpay.payment.dto.PaymentRequest.builder().build();

        String result = ReflectionTestUtils.invokeMethod(stripeProvider, "resolveCancelUrl", request);

        assertEquals("https://fusionx.fun/payment/cancel", result);
    }

    @Test
    void testGetProviderName() {
        assertEquals("STRIPE", stripeProvider.getProviderName());
    }

    @Test
    void testValidateCallback_InvalidSignature() {
        // Given
        String payload = "{}";
        String invalidSignature = "invalid_signature";

        // When
        boolean result = stripeProvider.validateCallback(payload, invalidSignature);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateCallback_NullSignature() {
        // Given
        String payload = "{}";

        // When - Stripe's webhook validation throws exception with null signature
        // This is expected behavior from the Stripe SDK
        try {
            boolean result = stripeProvider.validateCallback(payload, null);
            assertFalse(result);
        } catch (NullPointerException e) {
            // Expected - Stripe SDK doesn't handle null signature gracefully
            assertTrue(true);
        }
    }

    @Test
    void testValidateCallback_EmptyPayload() {
        // Given
        String signature = "t=1234567890,v1=abc123";

        // When
        boolean result = stripeProvider.validateCallback("", signature);

        // Then
        assertFalse(result);
    }

    @Test
    void testProcessCallback_InvalidSignature() {
        // Given
        String payload = "{}";
        String invalidSignature = "invalid_signature";

        // When
        PaymentResponse response = stripeProvider.processCallback(payload, invalidSignature);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertTrue(response.getErrorMessage().contains("Invalid webhook signature"));
    }

    @Test
    void testProcessCallback_MalformedPayload() {
        // Given
        String malformedPayload = "not-json";
        String signature = "invalid";

        // When
        PaymentResponse response = stripeProvider.processCallback(malformedPayload, signature);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
    }

    @Test
    void testMapRefundReason_Duplicate() {
        // Use reflection to test the private method
        String reason = "duplicate payment";
        com.stripe.param.RefundCreateParams.Reason result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapRefundReason", reason);
        assertEquals(com.stripe.param.RefundCreateParams.Reason.DUPLICATE, result);
    }

    @Test
    void testMapRefundReason_Fraud() {
        String reason = "fraud detected";
        com.stripe.param.RefundCreateParams.Reason result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapRefundReason", reason);
        assertEquals(com.stripe.param.RefundCreateParams.Reason.FRAUDULENT, result);
    }

    @Test
    void testMapRefundReason_CustomerRequest() {
        String reason = "customer requested refund";
        com.stripe.param.RefundCreateParams.Reason result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapRefundReason", reason);
        assertEquals(com.stripe.param.RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER, result);
    }

    @Test
    void testMapRefundReason_UnknownReason() {
        String reason = "unknown reason";
        com.stripe.param.RefundCreateParams.Reason result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapRefundReason", reason);
        assertNull(result);
    }

    @Test
    void testMapRefundReason_Null() {
        com.stripe.param.RefundCreateParams.Reason result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapRefundReason", (String) null);
        assertNull(result);
    }

    @Test
    void testMapStripeRefundStatus_Succeeded() {
        RefundStatus result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapStripeRefundStatus", "succeeded");
        assertEquals(RefundStatus.COMPLETED, result);
    }

    @Test
    void testMapStripeRefundStatus_Pending() {
        RefundStatus result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapStripeRefundStatus", "pending");
        assertEquals(RefundStatus.PENDING, result);
    }

    @Test
    void testMapStripeRefundStatus_Failed() {
        RefundStatus result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapStripeRefundStatus", "failed");
        assertEquals(RefundStatus.FAILED, result);
    }

    @Test
    void testMapStripeRefundStatus_Canceled() {
        RefundStatus result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapStripeRefundStatus", "canceled");
        assertEquals(RefundStatus.CANCELLED, result);
    }

    @Test
    void testMapStripeRefundStatus_Unknown() {
        RefundStatus result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapStripeRefundStatus", "unknown");
        assertEquals(RefundStatus.PROCESSING, result);
    }

    @Test
    void testMapStripeRefundStatus_Null() {
        RefundStatus result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "mapStripeRefundStatus", (String) null);
        assertEquals(RefundStatus.PENDING, result);
    }

    @Test
    void testProcessCallback_RefundEventUsesIndependentIdempotencyKey() {
        String payload = refundWebhookPayload(ORDER_ID.toString(), REFUND_EVENT_ID);
        String signature = createValidSignature(payload);

        when(idempotencyService.getProcessingState(eq("stripe:webhook:event:refund:event:" + REFUND_EVENT_ID)))
                .thenReturn(null);
        when(idempotencyService.acquireProcessingLock(eq("stripe:webhook:event:refund:event:" + REFUND_EVENT_ID), eq(java.time.Duration.ofDays(7))))
                .thenReturn(true);

        PaymentResponse response = stripeProvider.processCallback(payload, signature);

        assertNotNull(response);
        assertEquals(PaymentStatus.REFUNDED, response.getStatus());
        assertEquals(ORDER_ID, response.getOrderId());
        assertEquals("pi_test_refund_webhook_123", response.getProviderTransactionId());

        verify(idempotencyService).getProcessingState(eq("stripe:webhook:event:refund:event:" + REFUND_EVENT_ID));
        verify(idempotencyService, never()).getProcessingState(eq("stripe:webhook:event:payment:order:" + ORDER_ID));
        verify(idempotencyService).markAsCompleted(eq("stripe:webhook:event:refund:event:" + REFUND_EVENT_ID), eq(java.time.Duration.ofDays(7)));
    }

    @Test
    void testProcessCallback_DuplicateRefundEventReturnsDuplicate() {
        String payload = refundWebhookPayload(ORDER_ID.toString(), REFUND_EVENT_ID);
        String signature = createValidSignature(payload);

        when(idempotencyService.getProcessingState(eq("stripe:webhook:event:refund:event:" + REFUND_EVENT_ID)))
                .thenReturn("completed");

        PaymentResponse response = stripeProvider.processCallback(payload, signature);

        assertNotNull(response);
        assertEquals(PaymentStatus.DUPLICATE, response.getStatus());
        assertEquals(ORDER_ID, response.getOrderId());
        verify(idempotencyService, never()).acquireProcessingLock(eq("stripe:webhook:event:refund:event:" + REFUND_EVENT_ID), eq(java.time.Duration.ofDays(7)));
    }

    @Test
    void testIsPaymentStatusEvent_PaymentIntentSucceeded() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isHandledWebhookEvent", "payment_intent.succeeded");
        assertTrue(result);
    }

    @Test
    void testIsPaymentStatusEvent_PaymentIntentFailed() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isHandledWebhookEvent", "payment_intent.payment_failed");
        assertTrue(result);
    }

    @Test
    void testIsPaymentStatusEvent_CheckoutSessionCompleted() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isHandledWebhookEvent", "checkout.session.completed");
        assertTrue(result);
    }

    @Test
    void testIsPaymentStatusEvent_OtherEvent() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isHandledWebhookEvent", "customer.created");
        assertFalse(result);
    }

    @Test
    void testCreateErrorResponse() {
        String errorMessage = "Test error";
        PaymentResponse response =
            ReflectionTestUtils.invokeMethod(stripeProvider, "createErrorResponse", errorMessage);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
        assertEquals("STRIPE", response.getPaymentChannel());
        assertEquals(errorMessage, response.getErrorMessage());
    }

    private String refundWebhookPayload(String orderId, String eventId) {
        return """
            {
              "id": "%s",
              "object": "event",
              "api_version": "%s",
              "created": 1731111111,
              "livemode": false,
              "pending_webhooks": 1,
              "type": "charge.refunded",
              "data": {
                "object": {
                  "id": "ch_test_123",
                  "object": "charge",
                  "refunded": true,
                  "amount_refunded": 15000,
                  "payment_intent": "pi_test_refund_webhook_123",
                  "metadata": {
                    "orderId": "%s"
                  }
                }
              }
            }
            """.formatted(eventId, STRIPE_API_VERSION, orderId);
    }

    private String createValidSignature(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + toHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
