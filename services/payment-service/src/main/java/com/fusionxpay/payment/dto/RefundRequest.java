package com.fusionxpay.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for initiating a refund.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    /**
     * The original payment transaction ID.
     */
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    /**
     * The amount to refund. If null, full refund will be processed.
     */
    @Positive(message = "Refund amount must be positive")
    private BigDecimal amount;

    /**
     * The reason for the refund.
     */
    private String reason;

    /**
     * The currency code (e.g., USD, EUR).
     */
    private String currency;

    /**
     * The provider's capture/payment ID for refund processing.
     * Required for PayPal refunds.
     */
    private String captureId;
}
