package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a HATEOAS link in PayPal API responses.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Link {

    /**
     * The complete target URL.
     */
    private String href;

    /**
     * The link relation type (e.g., "self", "approve", "capture").
     */
    private String rel;

    /**
     * The HTTP method required for the link.
     */
    private String method;
}
