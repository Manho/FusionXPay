package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a monetary amount for PayPal transactions.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Amount {

    /**
     * The three-character ISO-4217 currency code.
     */
    @JsonProperty("currency_code")
    private String currencyCode;

    /**
     * The value of the amount (formatted as string with up to 2 decimal places).
     */
    private String value;
}
