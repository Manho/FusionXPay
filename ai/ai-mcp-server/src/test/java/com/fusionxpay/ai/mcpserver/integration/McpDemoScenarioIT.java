package com.fusionxpay.ai.mcpserver.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.audit.AuditStatus;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import com.fusionxpay.ai.common.dto.order.OrderRecord;
import com.fusionxpay.ai.common.dto.payment.PaymentPageResult;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.dto.payment.RefundResult;
import com.fusionxpay.ai.mcpserver.AiMcpServerApplication;
import com.fusionxpay.ai.mcpserver.tool.FusionXMcpTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {AiMcpServerApplication.class, McpDemoScenarioIT.TestAuditConfiguration.class},
        properties = {
                "fusionx.ai.audit.enabled=false",
                "fusionx.ai.mcp.auth.email=merchant@example.com",
                "fusionx.ai.mcp.auth.password=Secret123!",
                "fusionx.ai.mcp.safety.max-input-length=4000",
                "fusionx.ai.mcp.safety.max-summary-length=600"
        }
)
class McpDemoScenarioIT {

    private static final Long MERCHANT_ID = 4242L;
    private static final String JWT_TOKEN = "demo-jwt-token";
    private static final WireMockServer WIRE_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @Autowired
    private FusionXMcpTools tools;

    @Autowired
    private CapturingAuditEventPublisher auditEventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("fusionx.ai.gateway.base-url", WIRE_MOCK::baseUrl);
    }

    @BeforeAll
    static void startWireMock() {
        WIRE_MOCK.start();
    }

    @BeforeEach
    void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Test
    @DisplayName("Should execute the full MCP demo scenario and emit audit events")
    void shouldExecuteFullDemoScenario() {
        auditEventPublisher.clear();
        stubLogin();
        stubOrderSearch();
        stubPaymentSearch();

        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID initialTransactionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID refundTransactionId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        stubInitiatePayment(orderId, initialTransactionId);
        stubRefundPayment(initialTransactionId, refundTransactionId);

        OrderPageResult orders = tools.searchOrders("PENDING", null, "2026-03-01", "2026-03-31", 0, 20);
        assertThat(orders.getOrders()).hasSize(1);
        OrderRecord order = tools.getOrder(orderId.toString(), null);
        assertThat(order.getOrderId()).isEqualTo(orderId);

        PaymentPageResult payments = tools.searchPayments("PROCESSING", "2026-03-01", "2026-03-31", 0, 20);
        assertThat(payments.getPayments()).hasSize(1);

        ConfirmationResponse pendingPayment = tools.initiatePayment(
                orderId.toString(),
                "88.30",
                "USD",
                "STRIPE",
                "Stage 8 demo payment",
                "https://api.fusionx.fun/api/v1/payment/stripe/return",
                "https://fusionx.fun/payment/cancel",
                "https://fusionx.fun/payment/success",
                "demo-ref-1"
        );
        assertThat(pendingPayment.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_REQUIRED);

        ConfirmationResponse confirmedPayment = tools.confirmAction(pendingPayment.getToken());
        assertThat(confirmedPayment.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        PaymentRecord paymentRecord = objectMapper.convertValue(confirmedPayment.getResult(), PaymentRecord.class);
        assertThat(paymentRecord.getTransactionId()).isEqualTo(initialTransactionId);

        ConfirmationResponse pendingRefund = tools.refundPayment(
                initialTransactionId.toString(),
                "88.30",
                "Customer request",
                "USD",
                "capture-1"
        );
        assertThat(pendingRefund.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMATION_REQUIRED);

        ConfirmationResponse confirmedRefund = tools.confirmAction(pendingRefund.getToken());
        assertThat(confirmedRefund.getStatus()).isEqualTo(ConfirmationStatus.CONFIRMED);
        RefundResult refundResult = objectMapper.convertValue(confirmedRefund.getResult(), RefundResult.class);
        assertThat(refundResult.getTransactionId()).isEqualTo(refundTransactionId.toString());
        assertThat(refundResult.getStatus()).isEqualTo("SUCCEEDED");

        List<String> actionNames = auditEventPublisher.snapshot().stream()
                .map(AuditEvent::getActionName)
                .toList();
        assertThat(actionNames).containsExactly(
                "search_orders",
                "get_order",
                "search_payments",
                "initiate_payment",
                "confirm_action",
                "refund_payment",
                "confirm_action"
        );
        assertThat(auditEventPublisher.snapshot().stream().map(AuditEvent::getStatus))
                .containsExactly(
                        AuditStatus.SUCCESS,
                        AuditStatus.SUCCESS,
                        AuditStatus.SUCCESS,
                        AuditStatus.CONFIRMATION_REQUIRED,
                        AuditStatus.SUCCESS,
                        AuditStatus.CONFIRMATION_REQUIRED,
                        AuditStatus.SUCCESS
                );
        assertThat(auditEventPublisher.snapshot())
                .allSatisfy(event -> {
                    assertThat(event.getMerchantId()).isEqualTo(MERCHANT_ID);
                    assertThat(event.getCorrelationId()).isNotBlank();
                    assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0L);
                });

        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/api/v1/admin/auth/login"))
                .withRequestBody(matchingJsonPath("$.email", equalTo("merchant@example.com"))));
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/orders"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + JWT_TOKEN))
                .withHeader("X-Merchant-Id", equalTo(String.valueOf(MERCHANT_ID))));
        WIRE_MOCK.verify(getRequestedFor(urlEqualTo("/api/v1/orders/id/" + orderId))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + JWT_TOKEN))
                .withHeader("X-Merchant-Id", equalTo(String.valueOf(MERCHANT_ID))));
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo("/api/v1/payment/search"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + JWT_TOKEN))
                .withHeader("X-Merchant-Id", equalTo(String.valueOf(MERCHANT_ID))));
        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/api/v1/payment/request"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + JWT_TOKEN))
                .withHeader("X-Merchant-Id", equalTo(String.valueOf(MERCHANT_ID)))
                .withRequestBody(matchingJsonPath("$.successUrl", equalTo("https://fusionx.fun/payment/success"))));
        WIRE_MOCK.verify(postRequestedFor(urlEqualTo("/api/v1/payment/refund"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer " + JWT_TOKEN))
                .withHeader("X-Merchant-Id", equalTo(String.valueOf(MERCHANT_ID)))
                .withRequestBody(matchingJsonPath("$.transactionId", equalTo(initialTransactionId.toString()))));
    }

    private static void stubLogin() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/api/v1/admin/auth/login"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "token":"%s",
                                  "tokenType":"Bearer",
                                  "expiresIn":3600,
                                  "merchant":{
                                    "id":%d,
                                    "merchantCode":"M-4242",
                                    "merchantName":"Demo Merchant",
                                    "email":"merchant@example.com",
                                    "role":"MERCHANT"
                                  }
                                }
                                """.formatted(JWT_TOKEN, MERCHANT_ID))));
    }

    private static void stubOrderSearch() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/api/v1/orders"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "orders":[
                                    {
                                      "orderId":"%s",
                                      "orderNumber":"ORD-STAGE8-1",
                                      "userId":4242,
                                      "amount":88.30,
                                      "currency":"USD",
                                      "status":"PENDING",
                                      "updatedAt":"2026-04-01T10:00:00Z"
                                    }
                                  ],
                                  "page":0,
                                  "size":20,
                                  "totalElements":1,
                                  "totalPages":1,
                                  "first":true,
                                  "last":true
                                }
                                """.formatted(orderId))));
        WIRE_MOCK.stubFor(get(urlEqualTo("/api/v1/orders/id/" + orderId))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "orderId":"%s",
                                  "orderNumber":"ORD-STAGE8-1",
                                  "userId":4242,
                                  "amount":88.30,
                                  "currency":"USD",
                                  "status":"PENDING",
                                  "updatedAt":"2026-04-01T10:00:00Z"
                                }
                                """.formatted(orderId))));
    }

    private static void stubPaymentSearch() {
        UUID transactionId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/api/v1/payment/search"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "payments":[
                                    {
                                      "transactionId":"%s",
                                      "orderId":"%s",
                                      "amount":44.15,
                                      "currency":"USD",
                                      "paymentChannel":"STRIPE",
                                      "status":"PROCESSING",
                                      "success":true
                                    }
                                  ],
                                  "page":0,
                                  "size":20,
                                  "totalElements":1,
                                  "totalPages":1,
                                  "first":true,
                                  "last":true
                                }
                                """.formatted(transactionId, orderId))));
    }

    private static void stubInitiatePayment(UUID orderId, UUID transactionId) {
        WIRE_MOCK.stubFor(post(urlEqualTo("/api/v1/payment/request"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "transactionId":"%s",
                                  "orderId":"%s",
                                  "amount":88.30,
                                  "currency":"USD",
                                  "paymentChannel":"STRIPE",
                                  "status":"PROCESSING",
                                  "redirectUrl":"https://checkout.stripe.com/c/demo",
                                  "success":true
                                }
                                """.formatted(transactionId, orderId))));
    }

    private static void stubRefundPayment(UUID originalTransactionId, UUID refundTransactionId) {
        WIRE_MOCK.stubFor(post(urlEqualTo("/api/v1/payment/refund"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "transactionId":"%s",
                                  "paymentTransactionId":"%s",
                                  "status":"SUCCEEDED",
                                  "message":"Refund accepted"
                                }
                                """.formatted(refundTransactionId, originalTransactionId))));
    }

    @TestConfiguration
    static class TestAuditConfiguration {

        @Bean
        @Primary
        CapturingAuditEventPublisher auditEventPublisher() {
            return new CapturingAuditEventPublisher();
        }
    }

    static class CapturingAuditEventPublisher implements AuditEventPublisher {
        private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(AuditEvent event) {
            events.add(event);
        }

        List<AuditEvent> snapshot() {
            return new ArrayList<>(events);
        }

        void clear() {
            events.clear();
        }
    }
}
