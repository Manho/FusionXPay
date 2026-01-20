package com.fusionxpay.payment.provider;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.service.IdempotencyService;
import com.fusionxpay.payment.service.PayPalAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PayPalProvider
 *
 * These tests focus on the provider logic without calling actual PayPal APIs.
 * Integration tests with PayPal sandbox should be done separately.
 */
@ExtendWith(MockitoExtension.class)
public class PayPalProviderTest {

    @Mock
    private PayPalAuthService payPalAuthService;

    @Mock
    private IdempotencyService idempotencyService;

    private PayPalProvider payPalProvider;

    @BeforeEach
    void setUp() {
        payPalProvider = new PayPalProvider(payPalAuthService, idempotencyService);
        ReflectionTestUtils.setField(payPalProvider, "webhookId", "test-webhook-id");
        ReflectionTestUtils.setField(payPalProvider, "returnUrl", "https://example.com/return");
        ReflectionTestUtils.setField(payPalProvider, "cancelUrl", "https://example.com/cancel");
    }

    @Test
    void testGetProviderName() {
        assertEquals("PAYPAL", payPalProvider.getProviderName());
    }

    @Test
    void testValidateCallback_InvalidJson() {
        // Given
        String invalidJson = "not-valid-json";
        String payload = "{}";

        // When
        boolean result = payPalProvider.validateCallback(payload, invalidJson);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateCallback_NullHeaders() {
        // Given
        String payload = "{}";

        // When
        boolean result = payPalProvider.validateCallback(payload, null);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateCallback_EmptyHeaders() {
        // Given
        String payload = "{}";

        // When
        boolean result = payPalProvider.validateCallback(payload, "");

        // Then
        assertFalse(result);
    }

    @Test
    void testProcessCallback_InvalidSignature() {
        // Given
        String payload = "{}";
        String invalidHeaders = "invalid-json";

        // When
        PaymentResponse response = payPalProvider.processCallback(payload, invalidHeaders);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
    }

    @Test
    void testProcessCallback_MalformedPayload() {
        // Given
        String malformedPayload = "not-json";
        String headers = "{}";

        // When
        PaymentResponse response = payPalProvider.processCallback(malformedPayload, headers);

        // Then
        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getStatus());
    }

}
