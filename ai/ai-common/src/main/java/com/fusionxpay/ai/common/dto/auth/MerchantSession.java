package com.fusionxpay.ai.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSession {
    private String token;
    private GatewayMerchantInfo merchant;
    private boolean refreshable;
}
