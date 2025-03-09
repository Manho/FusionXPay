package com.fusionxpay.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationRequest {
    private String paymentId;
    private String paymentMethod;
    private String customerId;
    private Double amount;
}