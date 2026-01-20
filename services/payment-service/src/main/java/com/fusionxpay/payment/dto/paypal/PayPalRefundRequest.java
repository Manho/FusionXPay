package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for PayPal refund operation.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayPalRefundRequest {

    /**
     * The amount to refund.
     */
    private Amount amount;

    /**
     * The invoice ID for the refund.
     */
    @JsonProperty("invoice_id")
    private String invoiceId;

    /**
     * A note to the payer about the refund.
     */
    @JsonProperty("note_to_payer")
    private String noteToPayer;
}
