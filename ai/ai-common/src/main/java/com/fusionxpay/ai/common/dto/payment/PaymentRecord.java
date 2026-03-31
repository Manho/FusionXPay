package com.fusionxpay.ai.common.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fusionxpay.common.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRecord {
    private UUID transactionId;
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentChannel;
    private PaymentStatus status;
    private String redirectUrl;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String merchantReference;
    private String providerTransactionId;
    private boolean success;
}
