package com.fusionxpay.admin.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String merchantName;
    private String email;
    private String password;
    private String merchantCode;
}
