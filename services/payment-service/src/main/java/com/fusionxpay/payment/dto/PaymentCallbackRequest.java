package com.fusionxpay.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackRequest {
    private String transactionId;
    private String orderId;
    private String paymentChannel;
    private String status;
    private Double amount;
    private String currency;
    private String signature;
    private Map<String, String> metadata;
}
