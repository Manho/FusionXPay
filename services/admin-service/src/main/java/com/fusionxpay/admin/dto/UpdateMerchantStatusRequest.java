package com.fusionxpay.admin.dto;

import com.fusionxpay.admin.model.MerchantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMerchantStatusRequest {

    @NotNull
    private MerchantStatus status;
}
