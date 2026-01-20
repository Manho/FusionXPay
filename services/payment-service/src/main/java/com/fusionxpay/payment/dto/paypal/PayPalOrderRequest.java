package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a PayPal order.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayPalOrderRequest {

    /**
     * The intent for the order.
     * Valid values: CAPTURE (immediate capture), AUTHORIZE (authorize only)
     */
    private String intent;

    /**
     * An array of purchase units.
     */
    @JsonProperty("purchase_units")
    private List<PurchaseUnit> purchaseUnits;

    /**
     * Customize payer experience during the approval process.
     */
    @JsonProperty("application_context")
    private ApplicationContext applicationContext;
}
