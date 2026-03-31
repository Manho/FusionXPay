package com.fusionxpay.ai.common.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentRequest {
    private String transactionId;
    private BigDecimal amount;
    private String reason;
    private String currency;
    private String captureId;
}
