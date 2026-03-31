package com.fusionxpay.ai.common.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusResult {
    private UUID orderId;
    private String orderNumber;
    private String status;
    private LocalDateTime updatedAt;
}
