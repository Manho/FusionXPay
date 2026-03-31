package com.fusionxpay.ai.mcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.client.GatewayClient;
import com.fusionxpay.ai.common.dto.auth.GatewayMerchantInfo;
import com.fusionxpay.ai.common.dto.auth.MerchantSession;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.payment.InitiatePaymentRequest;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.dto.payment.RefundPaymentRequest;
import com.fusionxpay.ai.common.dto.payment.RefundResult;
import com.fusionxpay.ai.common.service.ConfirmationLookupResult;
import com.fusionxpay.ai.common.service.ConfirmationLookupStatus;
import com.fusionxpay.ai.common.service.ConfirmationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolServiceTest {

    private GatewayClient gatewayClient;
    private McpAuthenticationService authenticationService;
    private ConfirmationService confirmationService;
    private McpToolService toolService;

    @BeforeEach
    void setUp() {
        gatewayClient = mock(GatewayClient.class);
        authenticationService = mock(McpAuthenticationService.class);
        confirmationService = mock(ConfirmationService.class);
        toolService = new McpToolService(gatewayClient, authenticationService, confirmationService, new ObjectMapper().findAndRegisterModules());

        when(authenticationService.getCurrentSession()).thenReturn(MerchantSession.builder()
                .token("jwt-token")
                .merchant(GatewayMerchantInfo.builder()
                        .id(42L)
                        .merchantName("Demo Merchant")
                        .role("MERCHANT")
                        .build())
                .refreshable(false)
                .build());
    }

    @Test
    void initiatePaymentShouldReturnConfirmationRequired() {
        when(confirmationService.create(eq(42L), eq(ConfirmationOperationType.INITIATE_PAYMENT), any(), any()))
                .thenReturn(com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction.builder()
                        .token("confirm-1")
                        .merchantId(42L)
                        .operationType(ConfirmationOperationType.INITIATE_PAYMENT)
                        .status(ConfirmationStatus.CONFIRMATION_REQUIRED)
                        .summary("Initiate payment")
                        .expiresAt(Instant.now().plusSeconds(600))
                        .payload(Map.of("orderId", UUID.randomUUID().toString()))
                        .build());

        var response = toolService.initiatePayment(InitiatePaymentRequest.builder()
                .orderId(UUID.randomUUID())
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .paymentChannel("STRIPE")
                .build());

        assertThat(response.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_REQUIRED);
        assertThat(response.getToken()).isEqualTo("confirm-1");
    }

    @Test
    void confirmActionShouldExecutePendingPayment() {
        UUID orderId = UUID.randomUUID();
        PaymentRecord paymentRecord = PaymentRecord.builder()
                .orderId(orderId)
                .success(true)
                .build();
        when(confirmationService.consume("confirm-1", 42L))
                .thenReturn(ConfirmationLookupResult.of(
                        ConfirmationLookupStatus.READY,
                        com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction.builder()
                                .token("confirm-1")
                                .merchantId(42L)
                                .operationType(ConfirmationOperationType.INITIATE_PAYMENT)
                                .status(ConfirmationStatus.CONFIRMED)
                                .summary("Initiate payment")
                                .payload(Map.of(
                                        "orderId", orderId.toString(),
                                        "amount", new BigDecimal("10.00"),
                                        "currency", "USD",
                                        "paymentChannel", "STRIPE"
                                ))
                                .build()));
        when(gatewayClient.initiatePayment(eq("jwt-token"), eq(42L), any(InitiatePaymentRequest.class)))
                .thenReturn(paymentRecord);

        var response = toolService.confirmAction("confirm-1");

        assertThat(response.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        assertThat(response.getResult()).isEqualTo(paymentRecord);
        verify(gatewayClient).initiatePayment(eq("jwt-token"), eq(42L), any(InitiatePaymentRequest.class));
    }

    @Test
    void confirmActionShouldReturnExpiredStatus() {
        when(confirmationService.consume("confirm-1", 42L))
                .thenReturn(ConfirmationLookupResult.of(
                        ConfirmationLookupStatus.EXPIRED,
                        com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction.builder()
                                .token("confirm-1")
                                .merchantId(42L)
                                .operationType(ConfirmationOperationType.REFUND_PAYMENT)
                                .status(ConfirmationStatus.EXPIRED)
                                .summary("Expired refund")
                                .expiresAt(Instant.now())
                                .build()));

        var response = toolService.confirmAction("confirm-1");

        assertThat(response.getStatus()).isEqualTo(ConfirmationStatus.EXPIRED);
        assertThat(response.getResult()).isNull();
    }

    @Test
    void confirmActionShouldExecuteRefund() {
        RefundResult refundResult = RefundResult.builder()
                .transactionId("tx-1")
                .status("SUCCEEDED")
                .build();
        when(confirmationService.consume("confirm-2", 42L))
                .thenReturn(ConfirmationLookupResult.of(
                        ConfirmationLookupStatus.READY,
                        com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction.builder()
                                .token("confirm-2")
                                .merchantId(42L)
                                .operationType(ConfirmationOperationType.REFUND_PAYMENT)
                                .status(ConfirmationStatus.CONFIRMED)
                                .summary("Refund payment")
                                .payload(Map.of(
                                        "transactionId", "tx-1",
                                        "amount", new BigDecimal("5.00"),
                                        "reason", "Customer request",
                                        "currency", "USD"
                                ))
                                .build()));
        when(gatewayClient.refundPayment(eq("jwt-token"), eq(42L), any(RefundPaymentRequest.class)))
                .thenReturn(refundResult);

        var response = toolService.confirmAction("confirm-2");

        assertThat(response.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        assertThat(response.getResult()).isEqualTo(refundResult);
        verify(gatewayClient).refundPayment(eq("jwt-token"), eq(42L), any(RefundPaymentRequest.class));
    }
}
