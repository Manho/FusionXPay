package com.fusionxpay.admin.dto;

import com.fusionxpay.admin.model.MerchantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateMerchantRequest {

    @NotBlank
    @Size(max = 100)
    private String merchantName;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @Size(max = 32)
    private String merchantCode;

    private MerchantRole role;
}
