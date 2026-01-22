package com.fusionxpay.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order query parameters DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderQueryParams {

    private String status;
    private String orderNumber;
    private String startDate;
    private String endDate;
    private Long userId;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;
}
