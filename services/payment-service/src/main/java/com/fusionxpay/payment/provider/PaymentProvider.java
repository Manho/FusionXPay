package com.fusionxpay.payment.provider;

import com.fusionxpay.payment.dto.PaymentCallbackRequest;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;

/**
 * Payment Provider interface that defines core functionality required for payment processors
 */
public interface PaymentProvider {
    /**
     * Process a payment request
     * 
     * @param paymentRequest payment request details
     * @return payment response containing transaction ID and payment status
     */
    PaymentResponse processPayment(PaymentRequest paymentRequest);
    
    /**
     * Validate callback signature
     * 
     * @param payload callback request body
     * @param signature callback signature
     * @return true if signature is valid, false otherwise
     */
    boolean validateCallback(String payload, String signature);
    
    /**
     * Process payment provider's callback
     * 
     * @param payload callback request body
     * @param signature callback signature
     * @return payment response containing updated payment status
     */
    PaymentResponse processCallback(String payload, String signature);
    
    /**
     * Get payment provider name
     * 
     * @return payment provider name (e.g., "STRIPE" or "PAYPAL")
     */
    String getProviderName();
}
