package com.fusionxpay.admin.dto;

import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Merchant info DTO (excludes sensitive data)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantInfo {

    private Long id;
    private String merchantCode;
    private String merchantName;
    private String email;
    private MerchantRole role;

    /**
     * Convert from Merchant entity
     */
    public static MerchantInfo fromEntity(Merchant merchant) {
        return MerchantInfo.builder()
                .id(merchant.getId())
                .merchantCode(merchant.getMerchantCode())
                .merchantName(merchant.getMerchantName())
                .email(merchant.getEmail())
                .role(merchant.getRole())
                .build();
    }
}
