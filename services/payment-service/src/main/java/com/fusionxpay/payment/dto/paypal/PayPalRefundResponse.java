package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for PayPal refund operation.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayPalRefundResponse {

    /**
     * The PayPal-generated ID for the refund.
     */
    private String id;

    /**
     * The refund status.
     * Valid values: CANCELLED, FAILED, PENDING, COMPLETED
     */
    private String status;

    /**
     * The amount refunded.
     */
    private Amount amount;

    /**
     * The invoice ID associated with the refund.
     */
    @JsonProperty("invoice_id")
    private String invoiceId;

    /**
     * The note to the payer.
     */
    @JsonProperty("note_to_payer")
    private String noteToPayer;

    /**
     * An array of HATEOAS links.
     */
    private List<Link> links;

    /**
     * The date and time when the refund was created.
     */
    @JsonProperty("create_time")
    private String createTime;

    /**
     * The date and time when the refund was last updated.
     */
    @JsonProperty("update_time")
    private String updateTime;
}
