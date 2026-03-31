package com.fusionxpay.ai.common.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayErrorResponse {
    private String message;
    private String error;
    private String errorMessage;
}
