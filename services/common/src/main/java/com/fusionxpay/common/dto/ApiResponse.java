package com.fusionxpay.common.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    public static ApiResponse<?> error(int code, String message) {
        return ApiResponse.builder()
                .code(code)
                .message(message)
                .build();
    }
}
