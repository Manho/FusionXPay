package com.fusionxpay.payment.dto.paypal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for PayPal Webhook request headers.
 * These headers are required for webhook signature verification.
 *
 * @see <a href="https://developer.paypal.com/api/rest/webhooks/#link-signatureverification">PayPal Webhook Signature Verification</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayPalWebhookHeaders {

    /**
     * The algorithm used to generate the signature.
     * Header: PAYPAL-AUTH-ALGO
     */
    private String authAlgo;

    /**
     * The URL of the certificate used to sign the webhook.
     * Header: PAYPAL-CERT-URL
     */
    private String certUrl;

    /**
     * A unique ID for this webhook transmission.
     * Header: PAYPAL-TRANSMISSION-ID
     */
    private String transmissionId;

    /**
     * The signature of the webhook payload.
     * Header: PAYPAL-TRANSMISSION-SIG
     */
    private String transmissionSig;

    /**
     * The timestamp when the webhook was transmitted.
     * Header: PAYPAL-TRANSMISSION-TIME
     */
    private String transmissionTime;

    /**
     * Check if all required headers are present.
     */
    public boolean isComplete() {
        return authAlgo != null && !authAlgo.isEmpty() &&
               certUrl != null && !certUrl.isEmpty() &&
               transmissionId != null && !transmissionId.isEmpty() &&
               transmissionSig != null && !transmissionSig.isEmpty() &&
               transmissionTime != null && !transmissionTime.isEmpty();
    }
}
