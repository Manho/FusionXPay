package com.fusionxpay.payment.provider;

import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;

public interface PaymentProvider {
    PaymentResponse processPayment(PaymentRequest paymentRequest);
    boolean validateCallback(String payload, String signature);
    String getProviderName();
}
