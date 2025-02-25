package com.fusionxpay.order.event;

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
public class OrderEvent {
    private String eventType; // ORDER_CREATED, ORDER_UPDATED
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime timestamp;
}