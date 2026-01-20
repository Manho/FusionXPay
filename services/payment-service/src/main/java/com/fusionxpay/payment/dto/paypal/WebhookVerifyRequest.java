package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for PayPal webhook signature verification.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookVerifyRequest {

    /**
     * The algorithm used for signing (from PAYPAL-AUTH-ALGO header).
     */
    @JsonProperty("auth_algo")
    private String authAlgo;

    /**
     * The certificate URL (from PAYPAL-CERT-URL header).
     */
    @JsonProperty("cert_url")
    private String certUrl;

    /**
     * The transmission ID (from PAYPAL-TRANSMISSION-ID header).
     */
    @JsonProperty("transmission_id")
    private String transmissionId;

    /**
     * The transmission signature (from PAYPAL-TRANSMISSION-SIG header).
     */
    @JsonProperty("transmission_sig")
    private String transmissionSig;

    /**
     * The transmission time (from PAYPAL-TRANSMISSION-TIME header).
     */
    @JsonProperty("transmission_time")
    private String transmissionTime;

    /**
     * The webhook ID configured in PayPal developer dashboard.
     */
    @JsonProperty("webhook_id")
    private String webhookId;

    /**
     * The raw webhook event body as a JSON object.
     */
    @JsonProperty("webhook_event")
    private Object webhookEvent;
}
