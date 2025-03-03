package com.fusionxpay.api.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String username;
    private String apiKey;
}
