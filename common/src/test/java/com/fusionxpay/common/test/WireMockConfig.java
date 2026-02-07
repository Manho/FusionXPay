package com.fusionxpay.common.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class WireMockConfig {

    public static WireMockServer createWireMockServer(int port) {
        WireMockServer server = new WireMockServer(port);
        server.start();
        WireMock.configureFor("localhost", port);
        return server;
    }

    public static void stubStripePaymentSuccess(WireMockServer server) {
        server.stubFor(post(urlPathMatching("/v1/payment_intents.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pi_test_123\",\"status\":\"succeeded\",\"client_secret\":\"pi_test_123_secret\"}")));
    }

    public static void stubStripePaymentFailure(WireMockServer server) {
        server.stubFor(post(urlPathMatching("/v1/payment_intents.*"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"type\":\"card_error\",\"message\":\"Card declined\"}}")));
    }

    public static void stubStripeRefundSuccess(WireMockServer server) {
        server.stubFor(post(urlPathMatching("/v1/refunds.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"re_test_123\",\"status\":\"succeeded\"}")));
    }

    public static void stubPayPalPaymentSuccess(WireMockServer server) {
        server.stubFor(post(urlPathMatching("/v2/checkout/orders.*"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"ORDER_TEST_123\",\"status\":\"CREATED\"}")));
    }
}
