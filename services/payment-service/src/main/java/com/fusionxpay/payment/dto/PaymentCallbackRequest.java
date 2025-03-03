package com.fusionxpay.payment.dto;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class PaymentCallbackRequest {
    private UUID transactionId;
    private String status;
    private String provider;
    private String signature;
    private Map<String, String> additionalData;
}
