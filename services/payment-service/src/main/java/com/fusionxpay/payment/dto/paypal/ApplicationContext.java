package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Application context for PayPal checkout experience customization.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationContext {

    /**
     * The brand name displayed on the PayPal site.
     */
    @JsonProperty("brand_name")
    private String brandName;

    /**
     * The URL where the customer is redirected after approving the payment.
     */
    @JsonProperty("return_url")
    private String returnUrl;

    /**
     * The URL where the customer is redirected after canceling the payment.
     */
    @JsonProperty("cancel_url")
    private String cancelUrl;

    /**
     * The type of landing page to display.
     * Valid values: LOGIN, BILLING, NO_PREFERENCE
     */
    @JsonProperty("landing_page")
    private String landingPage;

    /**
     * Configures the label of the continue or pay now button.
     * Valid values: CONTINUE, PAY_NOW
     */
    @JsonProperty("user_action")
    private String userAction;

    /**
     * The shipping preference.
     * Valid values: GET_FROM_FILE, NO_SHIPPING, SET_PROVIDED_ADDRESS
     */
    @JsonProperty("shipping_preference")
    private String shippingPreference;
}
