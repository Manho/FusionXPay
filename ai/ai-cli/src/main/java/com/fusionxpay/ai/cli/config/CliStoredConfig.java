package com.fusionxpay.ai.cli.config;

import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CliStoredConfig {

    private String baseUrl;
    private String jwt;
    private String refreshToken;
    private String audience;
    private String tokenType;
    private String merchantEmail;
    private Long merchantId;
    private String merchantName;
    private String merchantRole;

    @Builder.Default
    private Map<String, PendingConfirmationAction> pendingConfirmations = new LinkedHashMap<>();
}
