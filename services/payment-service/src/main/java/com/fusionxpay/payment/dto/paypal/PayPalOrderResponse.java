package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for PayPal order creation and retrieval.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayPalOrderResponse {

    /**
     * The PayPal-generated ID for the order.
     */
    private String id;

    /**
     * The order status.
     * Valid values: CREATED, SAVED, APPROVED, VOIDED, COMPLETED, PAYER_ACTION_REQUIRED
     */
    private String status;

    /**
     * The intent for the order.
     */
    private String intent;

    /**
     * An array of purchase units.
     */
    @JsonProperty("purchase_units")
    private List<PurchaseUnit> purchaseUnits;

    /**
     * An array of HATEOAS links.
     */
    private List<Link> links;

    /**
     * The date and time when the order was created.
     */
    @JsonProperty("create_time")
    private String createTime;

    /**
     * The date and time when the order was last updated.
     */
    @JsonProperty("update_time")
    private String updateTime;
}
