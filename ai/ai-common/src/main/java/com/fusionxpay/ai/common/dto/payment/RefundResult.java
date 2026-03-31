package com.fusionxpay.ai.common.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefundResult {
    private String refundId;
    private String transactionId;
    private String providerRefundId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String paymentChannel;
    private String errorMessage;
    private LocalDateTime createdAt;
}
