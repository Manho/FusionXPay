package com.fusionxpay.admin.dto;

import com.fusionxpay.admin.model.MerchantApiKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyInfoResponse {

    private Long id;
    private String keyPrefix;
    private String lastFour;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime revokedAt;

    public static ApiKeyInfoResponse fromEntity(MerchantApiKey key) {
        return ApiKeyInfoResponse.builder()
                .id(key.getId())
                .keyPrefix(key.getKeyPrefix())
                .lastFour(key.getLastFour())
                .active(Boolean.TRUE.equals(key.getActive()))
                .createdAt(key.getCreatedAt())
                .updatedAt(key.getUpdatedAt())
                .revokedAt(key.getRevokedAt())
                .build();
    }
}
