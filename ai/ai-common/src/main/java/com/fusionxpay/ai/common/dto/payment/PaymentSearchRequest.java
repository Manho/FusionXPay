package com.fusionxpay.ai.common.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSearchRequest {
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
    private String status;
    private String from;
    private String to;
}
