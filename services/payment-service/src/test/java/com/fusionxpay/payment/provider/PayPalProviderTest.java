package com.fusionxpay.payment.provider;

import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.model.RefundStatus;
import com.fusionxpay.payment.dto.paypal.PayPalOrderResponse;
import com.fusionxpay.payment.service.IdempotencyService;
import com.fusionxpay.payment.service.PayPalAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    @Test
    void testCaptureOrder_OrderAlreadyCaptured_TreatAsIdempotent() {
        // Given
        String baseUrl = "https://api.sandbox.paypal.com";
        String paypalOrderId = "6GD03371FN609620H";

        when(payPalAuthService.getAccessToken()).thenReturn("test-access-token");
        when(payPalAuthService.getBaseUrl()).thenReturn(baseUrl);

        var restTemplate = (org.springframework.web.client.RestTemplate) ReflectionTestUtils.getField(payPalProvider, "restTemplate");
        assertNotNull(restTemplate);

        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(requestTo(baseUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"UNPROCESSABLE_ENTITY\",\"details\":[{\"issue\":\"ORDER_ALREADY_CAPTURED\"}]}"));

        server.expect(requestTo(baseUrl + "/v2/checkout/orders/" + paypalOrderId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"" + paypalOrderId + "\",\"status\":\"COMPLETED\",\"purchase_units\":[{\"payments\":{\"captures\":[{\"id\":\"CAPTURE123\",\"status\":\"COMPLETED\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        // When
        PayPalOrderResponse response = payPalProvider.captureOrder(paypalOrderId);

        // Then
        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        server.verify();
    }

    @Test
    void testProcessRefund_Success() {
        String baseUrl = "https://api.sandbox.paypal.com";
        when(payPalAuthService.getAccessToken()).thenReturn("test-access-token");
        when(payPalAuthService.getBaseUrl()).thenReturn(baseUrl);

        var restTemplate = (org.springframework.web.client.RestTemplate) ReflectionTestUtils.getField(payPalProvider, "restTemplate");
        assertNotNull(restTemplate);

        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(baseUrl + "/v2/payments/captures/CAPTURE-123/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id": "REFUND-123",
                          "status": "COMPLETED",
                          "amount": {
                            "currency_code": "USD",
                            "value": "50.00"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        RefundResponse response = payPalProvider.processRefund(ProviderRefundRequest.builder()
                .providerTransactionId("CAPTURE-123")
                .amount(new java.math.BigDecimal("50.00"))
                .currency("USD")
                .reason("Customer request")
                .build());

        assertNotNull(response);
        assertEquals(RefundStatus.COMPLETED, response.getStatus());
        assertEquals("REFUND-123", response.getProviderRefundId());
        assertEquals("PAYPAL", response.getPaymentChannel());
        assertEquals(new java.math.BigDecimal("50.00"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertNotNull(response.getRefundId());
        server.verify();
    }

    @Test
    void testProcessRefund_FailureReturnsFailedResponse() {
        String baseUrl = "https://api.sandbox.paypal.com";
        when(payPalAuthService.getAccessToken()).thenReturn("test-access-token");
        when(payPalAuthService.getBaseUrl()).thenReturn(baseUrl);

        var restTemplate = (org.springframework.web.client.RestTemplate) ReflectionTestUtils.getField(payPalProvider, "restTemplate");
        assertNotNull(restTemplate);

        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(requestTo(baseUrl + "/v2/payments/captures/CAPTURE-123/refund"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"INTERNAL_SERVER_ERROR\"}"));

        RefundResponse response = payPalProvider.processRefund(ProviderRefundRequest.builder()
                .providerTransactionId("CAPTURE-123")
                .amount(new java.math.BigDecimal("50.00"))
                .currency("USD")
                .reason("Customer request")
                .build());

        assertNotNull(response);
        assertEquals(RefundStatus.FAILED, response.getStatus());
        assertEquals("PAYPAL", response.getPaymentChannel());
        assertNull(response.getRefundId());
        assertTrue(response.getErrorMessage().contains("PayPal refund failed"));
        server.verify();
    }

    @Test
    void testMapPayPalRefundStatus_Completed() {
        RefundStatus result = ReflectionTestUtils.invokeMethod(payPalProvider, "mapPayPalRefundStatus", "COMPLETED");
        assertEquals(RefundStatus.COMPLETED, result);
    }

    @Test
    void testMapPayPalRefundStatus_UnknownDefaultsToProcessing() {
        RefundStatus result = ReflectionTestUtils.invokeMethod(payPalProvider, "mapPayPalRefundStatus", "UNKNOWN");
        assertEquals(RefundStatus.PROCESSING, result);
    }

    @Test
    void testMapPayPalRefundStatus_NullDefaultsToPending() {
        RefundStatus result = ReflectionTestUtils.invokeMethod(payPalProvider, "mapPayPalRefundStatus", (String) null);
        assertEquals(RefundStatus.PENDING, result);
    }

}
