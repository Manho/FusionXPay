package com.fusionxpay.ai.common.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchRequest {
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
    private String status;
    private String orderNumber;
    private String from;
    private String to;
}
