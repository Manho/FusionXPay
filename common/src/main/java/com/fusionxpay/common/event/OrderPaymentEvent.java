package com.fusionxpay.common.event; 

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
public class OrderPaymentEvent {
    private UUID orderId;
    private UUID transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String paymentChannel; 
    private String message;
    private LocalDateTime timestamp;
}