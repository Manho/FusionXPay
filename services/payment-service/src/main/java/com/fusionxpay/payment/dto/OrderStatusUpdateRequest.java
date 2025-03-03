package com.fusionxpay.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequest {
    private String status;
    private UUID paymentTransactionId;
    private String message;
}
