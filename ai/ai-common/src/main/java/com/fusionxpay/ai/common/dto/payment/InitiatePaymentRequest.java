package com.fusionxpay.ai.common.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRequest {
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentChannel;
    private String description;
    private String returnUrl;
    private String cancelUrl;
    private String successUrl;
    private String merchantReference;
}
