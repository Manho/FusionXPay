package com.fusionxpay.payment.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderPaymentEvent {
    private UUID orderId;
    private UUID transactionId;
    private String status;
    private long timestamp;
}
