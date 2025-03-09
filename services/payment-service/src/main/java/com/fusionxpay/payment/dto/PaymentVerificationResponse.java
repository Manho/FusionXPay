package com.fusionxpay.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentVerificationResponse {
    private String paymentId;
    private boolean verified;
    private String message;
    private String transactionId;
    private String orderId;
    private String paymentChannel;
    private Double amount;
    private String currency;
    private String status;
    private String errorMessage;
}