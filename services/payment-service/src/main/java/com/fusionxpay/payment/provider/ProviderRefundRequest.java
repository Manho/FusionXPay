package com.fusionxpay.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Internal refund request contract for payment providers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRefundRequest {

    @NonNull
    private String providerTransactionId;

    private BigDecimal amount;

    private String currency;

    private String reason;
}
