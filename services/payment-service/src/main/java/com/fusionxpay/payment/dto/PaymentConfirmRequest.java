package com.fusionxpay.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request payload for confirming payment status by querying provider APIs directly.
 * This is intended as a fallback when inbound webhooks/callbacks are unavailable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {
    @NotNull
    private UUID orderId;

    /**
     * Optional. If omitted, the system uses the stored transaction paymentChannel.
     */
    private String paymentChannel;

    /**
     * Optional. If omitted, the system uses the stored providerTransactionId.
     * For Stripe this is usually a checkout session id (cs_...) before confirmation,
     * and a payment intent id (pi_...) after confirmation.
     */
    private String providerReferenceId;
}

