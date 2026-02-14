package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a purchase unit in a PayPal order.
 * Contains item details, amount, and reference information.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseUnit {

    /**
     * API caller-provided external ID for the purchase unit.
     * Used to reconcile transactions.
     */
    @JsonProperty("reference_id")
    private String referenceId;

    /**
     * Custom ID for the purchase unit.
     * Used to pass internal order ID.
     */
    @JsonProperty("custom_id")
    private String customId;

    /**
     * The total order amount.
     */
    private Amount amount;

    /**
     * Description of the purchase.
     */
    private String description;

    /**
     * The soft descriptor for the transaction.
     */
    @JsonProperty("soft_descriptor")
    private String softDescriptor;

    /**
     * Payments information returned by capture/get order responses.
     */
    private Payments payments;
}
