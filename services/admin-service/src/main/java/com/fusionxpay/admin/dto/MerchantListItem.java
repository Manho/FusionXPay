package com.fusionxpay.admin.dto;

import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.model.MerchantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantListItem {

    private Long id;
    private String merchantCode;
    private String merchantName;
    private String email;
    private MerchantRole role;
    private MerchantStatus status;
    private LocalDateTime createdAt;

    public static MerchantListItem fromEntity(Merchant merchant) {
        return MerchantListItem.builder()
                .id(merchant.getId())
                .merchantCode(merchant.getMerchantCode())
                .merchantName(merchant.getMerchantName())
                .email(merchant.getEmail())
                .role(merchant.getRole())
                .status(merchant.getStatus())
                .createdAt(merchant.getCreatedAt())
                .build();
    }
}
