package com.fusionxpay.ai.common.exception;

public class AiAuthenticationException extends AiGatewayException {

    public AiAuthenticationException(int statusCode, String message, String responseBody, Throwable cause) {
        super(statusCode, message, responseBody, cause);
    }
}
