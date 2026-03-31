package com.fusionxpay.ai.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.fusionxpay.ai.common.dto.auth.GatewayLoginResponse;
import com.fusionxpay.ai.common.dto.order.OrderPageResult;
import com.fusionxpay.ai.common.dto.order.OrderSearchRequest;
import com.fusionxpay.ai.common.dto.payment.PaymentRecord;
import com.fusionxpay.ai.common.exception.AiAuthenticationException;
import com.fusionxpay.common.model.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayClientTest {

    private WireMockServer wireMockServer;
    private GatewayClient gatewayClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(5_000);
        gatewayClient = new GatewayClient(
                RestClient.builder()
                        .baseUrl(wireMockServer.baseUrl())
                        .requestFactory(requestFactory)
                        .build(),
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void loginShouldReturnJwtSession() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/admin/auth/login"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "token":"jwt-token",
                                  "tokenType":"Bearer",
                                  "expiresIn":3600,
                                  "merchant":{
                                    "id":42,
                                    "merchantCode":"M-42",
                                    "merchantName":"Demo Merchant",
                                    "email":"merchant@example.com",
                                    "role":"MERCHANT"
                                  }
                                }
                                """)));

        GatewayLoginResponse response = gatewayClient.login("merchant@example.com", "secret123");

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getMerchant().getId()).isEqualTo(42L);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/admin/auth/login"))
                .withRequestBody(matchingJsonPath("$.email", equalTo("merchant@example.com"))));
    }

    @Test
    void searchOrdersShouldSendMerchantHeadersAndFilters() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/orders"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "orders":[
                                    {
                                      "orderId":"c8af432f-70e1-4ac6-a83f-07c85ebea8ad",
                                      "orderNumber":"ORD-1001",
                                      "userId":7,
                                      "amount":100.50,
                                      "currency":"USD",
                                      "status":"PAID"
                                    }
                                  ],
                                  "page":0,
                                  "size":20,
                                  "totalElements":1,
                                  "totalPages":1,
                                  "first":true,
                                  "last":true
                                }
                                """)));

        OrderPageResult response = gatewayClient.searchOrders(
                "jwt-token",
                7L,
                OrderSearchRequest.builder()
                        .status("PAID")
                        .orderNumber("ORD-1001")
                        .from("2026-03-01")
                        .to("2026-03-31")
                        .build()
        );

        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getOrderNumber()).isEqualTo("ORD-1001");

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/v1/orders"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer jwt-token"))
                .withHeader("X-Merchant-Id", equalTo("7")));
    }

    @Test
    void queryPaymentShouldTranslateUnauthorizedErrors() {
        UUID transactionId = UUID.randomUUID();
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/payment/transaction/" + transactionId))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {"message":"JWT expired"}
                                """)));

        assertThatThrownBy(() -> gatewayClient.queryPayment("expired-token", 9L, transactionId))
                .isInstanceOf(AiAuthenticationException.class)
                .hasMessageContaining("JWT expired");
    }

    @Test
    void initiatePaymentShouldMapSuccessfulResponse() {
        UUID transactionId = UUID.randomUUID();
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/payment/request"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody("""
                                {
                                  "transactionId":"%s",
                                  "orderId":"6c8d1b54-1ea8-4fd0-9f0e-22d496e60d38",
                                  "amount":88.30,
                                  "currency":"USD",
                                  "paymentChannel":"STRIPE",
                                  "status":"PROCESSING",
                                  "redirectUrl":"https://checkout.example.com/session/abc",
                                  "success":true
                                }
                                """.formatted(transactionId))));

        PaymentRecord response = gatewayClient.initiatePayment(
                "jwt-token",
                11L,
                com.fusionxpay.ai.common.dto.payment.InitiatePaymentRequest.builder()
                        .orderId(UUID.fromString("6c8d1b54-1ea8-4fd0-9f0e-22d496e60d38"))
                        .amount(new BigDecimal("88.30"))
                        .currency("USD")
                        .paymentChannel("STRIPE")
                        .successUrl("https://fusionx.fun/payment/success")
                        .cancelUrl("https://fusionx.fun/payment/cancel")
                        .build()
        );

        assertThat(response.getTransactionId()).isEqualTo(transactionId);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(response.getRedirectUrl()).isEqualTo("https://checkout.example.com/session/abc");
    }
}
