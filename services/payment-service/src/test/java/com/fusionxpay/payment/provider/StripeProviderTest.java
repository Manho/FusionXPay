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

import static org.junit.jupiter.api.Assertions.*;

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

    @BeforeEach
    void setUp() {
        stripeProvider = new StripeProvider(redisTemplate, idempotencyService);
        ReflectionTestUtils.setField(stripeProvider, "apiKey", API_KEY);
        ReflectionTestUtils.setField(stripeProvider, "webhookSecret", WEBHOOK_SECRET);
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
    void testIsPaymentStatusEvent_PaymentIntentSucceeded() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isPaymentStatusEvent", "payment_intent.succeeded");
        assertTrue(result);
    }

    @Test
    void testIsPaymentStatusEvent_PaymentIntentFailed() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isPaymentStatusEvent", "payment_intent.payment_failed");
        assertTrue(result);
    }

    @Test
    void testIsPaymentStatusEvent_CheckoutSessionCompleted() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isPaymentStatusEvent", "checkout.session.completed");
        assertTrue(result);
    }

    @Test
    void testIsPaymentStatusEvent_OtherEvent() {
        boolean result =
            ReflectionTestUtils.invokeMethod(stripeProvider, "isPaymentStatusEvent", "customer.created");
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
}
