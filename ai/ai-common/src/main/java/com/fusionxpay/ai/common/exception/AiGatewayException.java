package com.fusionxpay.ai.common.exception;

import lombok.Getter;

@Getter
public class AiGatewayException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public AiGatewayException(int statusCode, String message, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
