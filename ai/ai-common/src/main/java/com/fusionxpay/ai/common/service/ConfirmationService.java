package com.fusionxpay.ai.common.service;

import com.fusionxpay.ai.common.dto.confirmation.ConfirmationOperationType;
import com.fusionxpay.ai.common.dto.confirmation.PendingConfirmationAction;

import java.util.Map;

public interface ConfirmationService {

    PendingConfirmationAction create(Long merchantId,
                                     ConfirmationOperationType operationType,
                                     String summary,
                                     Map<String, Object> payload);

    ConfirmationLookupResult consume(String token, Long merchantId);
}
