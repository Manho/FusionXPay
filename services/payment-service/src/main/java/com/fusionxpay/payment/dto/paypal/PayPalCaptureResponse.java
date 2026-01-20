package com.fusionxpay.payment.dto.paypal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for PayPal order capture operation.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayPalCaptureResponse {

    /**
     * The PayPal-generated ID for the captured payment.
     */
    private String id;

    /**
     * The status of the captured payment.
     * Valid values: COMPLETED, DECLINED, PARTIALLY_REFUNDED, PENDING, REFUNDED, FAILED
     */
    private String status;

    /**
     * The amount captured.
     */
    private Amount amount;

    /**
     * The capture ID for refund operations.
     */
    @JsonProperty("final_capture")
    private Boolean finalCapture;

    /**
     * The date and time when the capture was created.
     */
    @JsonProperty("create_time")
    private String createTime;

    /**
     * The date and time when the capture was last updated.
     */
    @JsonProperty("update_time")
    private String updateTime;
}
