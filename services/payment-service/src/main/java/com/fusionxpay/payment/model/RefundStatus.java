package com.fusionxpay.payment.model;

/**
 * Enumeration of refund statuses.
 *
 * @author FusionXPay Team
 * @since 1.0.0
 */
public enum RefundStatus {

    /**
     * Refund has been initiated and is pending.
     */
    PENDING,

    /**
     * Refund is being processed.
     */
    PROCESSING,

    /**
     * Refund has been completed successfully.
     */
    COMPLETED,

    /**
     * Refund has failed.
     */
    FAILED,

    /**
     * Refund was cancelled.
     */
    CANCELLED
}
