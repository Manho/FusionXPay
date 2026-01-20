package com.fusionxpay.payment.dto;

import com.fusionxpay.payment.model.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for refund operations.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    /**
     * The refund ID (internal).
     */
    private String refundId;

    /**
     * The original transaction ID.
     */
    private String transactionId;

    /**
     * The provider's refund ID.
     */
    private String providerRefundId;

    /**
     * The refund status.
     */
    private RefundStatus status;

    /**
     * The refunded amount.
     */
    private BigDecimal amount;

    /**
     * The currency code.
     */
    private String currency;

    /**
     * The payment channel (STRIPE, PAYPAL).
     */
    private String paymentChannel;

    /**
     * Error message if refund failed.
     */
    private String errorMessage;

    /**
     * The timestamp when the refund was created.
     */
    private LocalDateTime createdAt;
}
