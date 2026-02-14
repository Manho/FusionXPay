package com.fusionxpay.payment.service;

import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.dto.RefundRequest;
import com.fusionxpay.payment.dto.RefundResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.model.RefundStatus;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
import com.fusionxpay.payment.provider.PayPalProvider;
import com.fusionxpay.payment.provider.StripeProvider;
import com.fusionxpay.payment.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProviderFactory paymentProviderFactory;
    private final OrderEventProducer orderEventProducer;

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findTransactionByOrderId(UUID orderId) {
        return paymentTransactionRepository.findByOrderId(orderId);
    }

    /**
     * Initiates a payment transaction
     * 
     * @param paymentRequest the payment request details
     * @return PaymentResponse with transaction details and redirect URL
     */
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest paymentRequest) {
        log.info("Initiating payment for order: {}", paymentRequest.getOrderId());
        
        // Check if there's already a transaction for this order
        Optional<PaymentTransaction> existingTransaction = 
                paymentTransactionRepository.findByOrderId(paymentRequest.getOrderId());
        
        if (existingTransaction.isPresent()) {
            PaymentTransaction transaction = existingTransaction.get();
            
            // If the transaction is already successful, return it
            if (PaymentStatus.SUCCESS.name().equals(transaction.getStatus())) {
                log.info("Payment for order {} already successful", paymentRequest.getOrderId());
                return mapTransactionToResponse(transaction, null, null);
            }
            
            // If the transaction is in PROCESSING state, return it
            if (PaymentStatus.PROCESSING.name().equals(transaction.getStatus())) {
                log.info("Payment for order {} is already processing", paymentRequest.getOrderId());
                return mapTransactionToResponse(transaction, null, null);
            }
            
            // If the transaction failed, we can try again with a new one
            log.info("Previous payment for order {} failed, creating new transaction", paymentRequest.getOrderId());
        }
        
        // Get the payment provider
        PaymentProvider provider = paymentProviderFactory.getProvider(paymentRequest.getPaymentChannel());
        
        // Create a new payment transaction
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderId(paymentRequest.getOrderId());
        transaction.setAmount(paymentRequest.getAmount());
        transaction.setCurrency(paymentRequest.getCurrency());
        transaction.setPaymentChannel(paymentRequest.getPaymentChannel());
        transaction.setStatus(PaymentStatus.INITIATED.name());
        
        // Save the transaction
        transaction = paymentTransactionRepository.save(transaction);
        log.info("Created payment transaction: {}", transaction.getTransactionId());
        
        try {
            // Process the payment with the provider
            PaymentResponse providerResponse = provider.processPayment(paymentRequest);
            
            // Update transaction with provider response details
            transaction.setStatus(providerResponse.getStatus().name());
            
            // Store the provider's transaction ID (e.g., Stripe session ID)
            if (providerResponse.getProviderTransactionId() != null) {
                transaction.setProviderTransactionId(providerResponse.getProviderTransactionId());
                log.info("Stored provider transaction ID: {}", providerResponse.getProviderTransactionId());
            }
            
            transaction = paymentTransactionRepository.save(transaction);
            
            // Notify the order service about the payment initiation
            orderEventProducer.sendPaymentStatusUpdate(
                    transaction.getOrderId(), 
                    transaction.getTransactionId(), 
                    PaymentStatus.valueOf(transaction.getStatus())
            );
            
            return mapTransactionToResponse(transaction, providerResponse.getRedirectUrl(), providerResponse.getErrorMessage());
        } catch (Exception e) {
            // Handle any exceptions during payment processing
            log.error("Payment processing failed: {}", e.getMessage(), e);
            
            // Update transaction to failed status
            transaction.setStatus(PaymentStatus.FAILED.name());
            transaction = paymentTransactionRepository.save(transaction);
            
            // Notify order service about the failure
            orderEventProducer.sendPaymentStatusUpdate(
                    transaction.getOrderId(),
                    transaction.getTransactionId(),
                    PaymentStatus.FAILED
            );
            
            // Return error response with our internal transaction ID
            return PaymentResponse.builder()
                    .transactionId(transaction.getTransactionId())
                    .orderId(transaction.getOrderId())
                    .status(PaymentStatus.FAILED)
                    .paymentChannel(transaction.getPaymentChannel())
                    .errorMessage("Payment processing failed: " + e.getMessage())
                    .build();
        }
    }
    
    @Transactional
    public boolean handleCallback(String payload, String signature, String provider) {
        log.info("Processing {} webhook callback", provider);
        
        try {
            // Get the appropriate payment provider
            PaymentProvider paymentProvider = paymentProviderFactory.getProvider(provider);
            
            // Validate callback signature
            if (!paymentProvider.validateCallback(payload, signature)) {
                log.error("Invalid webhook signature for provider: {}", provider);
                return false;
            }

            // Process the callback and get response
            PaymentResponse response = paymentProvider.processCallback(payload, signature);
            if (response == null) {
                log.info("Ignoring unhandled event from provider: {}", provider);
                return true; // Unhandled event type, but not an error
            }

            // Idempotency check: ensure we don't process the same event twice
            if (PaymentStatus.DUPLICATE.name().equals(response.getStatus().name())) {
                log.info("Transaction orderId: {} already successfully processed", response.getOrderId());
                return true;
            }
            // If the event is being processed concurrently by another instance
            if (PaymentStatus.PROCESSING.equals(response.getStatus())) {
                log.info("Transaction orderId: {} is currently being processed by another instance", response.getOrderId());
                return true; // Consider this a success, another instance is handling it
            }

            if (response.getOrderId() == null) {
                log.warn("Callback response from provider {} missing Order ID. Will try to resolve by providerTransactionId.", provider);
            }
            
            // Find and update transaction record
            Optional<PaymentTransaction> optionalTransaction = Optional.empty();
            if (response.getOrderId() != null) {
                optionalTransaction = paymentTransactionRepository.findByOrderId(response.getOrderId());
            }
            if (optionalTransaction.isEmpty()
                    && response.getProviderTransactionId() != null
                    && !response.getProviderTransactionId().isBlank()) {
                optionalTransaction = paymentTransactionRepository.findByProviderTransactionId(response.getProviderTransactionId());
            }
            
            if (optionalTransaction.isEmpty()) {
                log.error("Transaction not found for provider {} (orderId={}, providerTransactionId={})",
                        provider, response.getOrderId(), response.getProviderTransactionId());
                return false;
            }

            PaymentTransaction transaction = optionalTransaction.get();
            if (response.getOrderId() == null) {
                response.setOrderId(transaction.getOrderId());
            }
            
            // Log status change
            log.info("Updating transaction {} status: {} -> {}", 
                transaction.getTransactionId(), transaction.getStatus(), response.getStatus().name());
                
            // Update transaction status
            transaction.setStatus(response.getStatus().name());
            if (response.getProviderTransactionId() != null && !response.getProviderTransactionId().isBlank()) {
                transaction.setProviderTransactionId(response.getProviderTransactionId());
            }
            paymentTransactionRepository.save(transaction);

            // Notify order service about the status update
            orderEventProducer.sendPaymentStatusUpdate(
                transaction.getOrderId(),
                transaction.getTransactionId(),
                response.getStatus()
            );
            
            log.info("Successfully processed {} webhook for transaction: {}", 
                provider, transaction.getTransactionId());
            return true;
        } catch (Exception e) {
            log.error("Error processing {} webhook: {}", provider, e.getMessage(), e);
            // Return false instead of throwing to avoid potential duplicate processing from retries
            return false;
        }
    }

    /**
     * Gets a payment transaction by ID
     * 
     * @param transactionId the transaction ID
     * @return PaymentResponse with transaction details or NOT_FOUND status
     */
    public PaymentResponse getPaymentTransaction(UUID transactionId) {
        log.info("Getting payment transaction: {}", transactionId);
        
        Optional<PaymentTransaction> optionalTransaction = paymentTransactionRepository.findById(transactionId);
        
        if (optionalTransaction.isEmpty()) {
            log.warn("Transaction not found: {}", transactionId);
            return PaymentResponse.builder()
                    .transactionId(transactionId)
                    .status(PaymentStatus.NOT_FOUND)
                    .errorMessage("Payment transaction not found")
                    .build();
        }
        
        return mapTransactionToResponse(optionalTransaction.get(), null, null);
    }
    
    /**
     * Gets a payment transaction by order ID
     * 
     * @param orderId the order ID
     * @return PaymentResponse with transaction details or NOT_FOUND status
     */
    public PaymentResponse getPaymentTransactionByOrderId(UUID orderId) {
        log.info("Getting payment transaction for order: {}", orderId);
        
        Optional<PaymentTransaction> optionalTransaction = paymentTransactionRepository.findByOrderId(orderId);
        
        if (optionalTransaction.isEmpty()) {
            log.warn("Transaction not found for order: {}", orderId);
            return PaymentResponse.builder()
                    .orderId(orderId)
                    .status(PaymentStatus.NOT_FOUND)
                    .errorMessage("Payment transaction not found for this order")
                    .build();
        }
        
        return mapTransactionToResponse(optionalTransaction.get(), null, null);
    }
    
    /**
     * Gets a list of all available payment providers
     * 
     * @return list of provider names
     */
    public List<String> getAvailablePaymentProviders() {
        return List.of("STRIPE", "PAYPAL");
    }
    
    /**
     * Process a payment request using the appropriate provider
     * 
     * @param paymentRequest the payment request details
     * @return PaymentResponse with transaction details
     */
    public PaymentResponse processPayment(PaymentRequest paymentRequest) {
        log.info("Processing payment for order: {}", paymentRequest.getOrderId());
        
        // Get the payment provider
        PaymentProvider provider = paymentProviderFactory.getProvider(paymentRequest.getPaymentChannel());
        
        // Process the payment with the provider
        return provider.processPayment(paymentRequest);
    }
    
    /**
     * Maps a PaymentTransaction entity to a PaymentResponse DTO
     *
     * @param transaction the transaction entity
     * @param redirectUrl the redirect URL from the provider
     * @return PaymentResponse DTO
     */
    private PaymentResponse mapTransactionToResponse(PaymentTransaction transaction, String redirectUrl, String errorMessage) {
        PaymentStatus parsedStatus;
        try {
            parsedStatus = PaymentStatus.valueOf(transaction.getStatus());
        } catch (IllegalArgumentException ex) {
            parsedStatus = PaymentStatus.FAILED;
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Unknown payment status: " + transaction.getStatus();
            } else {
                errorMessage = errorMessage + " (unknown payment status: " + transaction.getStatus() + ")";
            }
        }

        return PaymentResponse.builder()
                .transactionId(transaction.getTransactionId())
                .orderId(transaction.getOrderId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentChannel(transaction.getPaymentChannel())
                .status(parsedStatus)
                .redirectUrl(redirectUrl)
                .errorMessage(errorMessage)
                .providerTransactionId(transaction.getProviderTransactionId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }

    /**
     * Updates the payment status for a given order.
     * Used by callback handlers to update payment state.
     *
     * @param orderId the order ID
     * @param status the new payment status
     * @return true if update was successful, false otherwise
     */
    @Transactional
    public boolean updatePaymentStatus(UUID orderId, PaymentStatus status) {
        log.info("Updating payment status for order {} to {}", orderId, status);

        Optional<PaymentTransaction> optionalTransaction =
                paymentTransactionRepository.findByOrderId(orderId);

        if (optionalTransaction.isEmpty()) {
            log.warn("Transaction not found for order: {}", orderId);
            return false;
        }

        PaymentTransaction transaction = optionalTransaction.get();
        String previousStatus = transaction.getStatus();

        transaction.setStatus(status.name());
        paymentTransactionRepository.save(transaction);

        log.info("Updated transaction {} status: {} -> {}",
                transaction.getTransactionId(), previousStatus, status);

        // Notify order service about the status update
        orderEventProducer.sendPaymentStatusUpdate(
                transaction.getOrderId(),
                transaction.getTransactionId(),
                status
        );

        return true;
    }

    /**
     * Updates the stored provider transaction ID for an order. This is required for refunds,
     * because providers often return a placeholder identifier during checkout (e.g. checkout session/order ID),
     * and a different identifier after capture/confirmation (e.g. PaymentIntent/capture ID).
     */
    @Transactional
    public boolean updateProviderTransactionId(UUID orderId, String providerTransactionId) {
        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            return false;
        }

        Optional<PaymentTransaction> optionalTransaction = paymentTransactionRepository.findByOrderId(orderId);
        if (optionalTransaction.isEmpty()) {
            log.warn("Transaction not found for order {} while updating providerTransactionId", orderId);
            return false;
        }

        PaymentTransaction transaction = optionalTransaction.get();
        transaction.setProviderTransactionId(providerTransactionId);
        paymentTransactionRepository.save(transaction);
        return true;
    }

    /**
     * Initiates a refund for a payment transaction.
     *
     * @param refundRequest the refund request details
     * @return RefundResponse with refund details
     */
    @Transactional
    public RefundResponse initiateRefund(RefundRequest refundRequest) {
        log.info("Initiating refund for transaction: {}", refundRequest.getTransactionId());

        // Find the original transaction
        UUID transactionId;
        try {
            transactionId = UUID.fromString(refundRequest.getTransactionId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid transaction ID format: {}", refundRequest.getTransactionId());
            return RefundResponse.builder()
                    .status(RefundStatus.FAILED)
                    .errorMessage("Invalid transaction ID format")
                    .build();
        }

        Optional<PaymentTransaction> optionalTransaction =
                paymentTransactionRepository.findById(transactionId);

        if (optionalTransaction.isEmpty()) {
            log.error("Transaction not found: {}", transactionId);
            return RefundResponse.builder()
                    .status(RefundStatus.FAILED)
                    .errorMessage("Transaction not found")
                    .build();
        }

        PaymentTransaction transaction = optionalTransaction.get();

        // Check if transaction is in a refundable state
        if (!PaymentStatus.SUCCESS.name().equals(transaction.getStatus())) {
            log.error("Transaction {} is not in a refundable state: {}",
                    transactionId, transaction.getStatus());
            return RefundResponse.builder()
                    .status(RefundStatus.FAILED)
                    .transactionId(transactionId.toString())
                    .errorMessage("Transaction is not in a refundable state")
                    .build();
        }

        // Get the provider transaction ID (Stripe PaymentIntent ID or PayPal Capture ID)
        String providerTransactionId = transaction.getProviderTransactionId();
        if (providerTransactionId == null || providerTransactionId.isEmpty()) {
            log.error("No provider transaction ID found for transaction: {}", transactionId);
            return RefundResponse.builder()
                    .status(RefundStatus.FAILED)
                    .transactionId(transactionId.toString())
                    .errorMessage("No provider transaction ID found")
                    .build();
        }

        // Process refund based on payment channel
        String paymentChannel = transaction.getPaymentChannel();
        RefundResponse refundResponse;

        try {
            if ("STRIPE".equalsIgnoreCase(paymentChannel)) {
                StripeProvider stripeProvider = (StripeProvider) paymentProviderFactory.getProvider("STRIPE");
                refundResponse = stripeProvider.processRefund(
                        providerTransactionId,
                        refundRequest.getAmount(),
                        refundRequest.getReason()
                );
            } else if ("PAYPAL".equalsIgnoreCase(paymentChannel)) {
                PayPalProvider payPalProvider = (PayPalProvider) paymentProviderFactory.getProvider("PAYPAL");
                // For PayPal, we need the capture ID which should be stored in providerTransactionId
                var paypalResponse = payPalProvider.processRefund(
                        refundRequest.getCaptureId() != null ? refundRequest.getCaptureId() : providerTransactionId,
                        refundRequest.getAmount(),
                        refundRequest.getCurrency() != null ? refundRequest.getCurrency() : transaction.getCurrency(),
                        refundRequest.getReason()
                );

                // Map PayPal response to RefundResponse
                refundResponse = RefundResponse.builder()
                        .refundId(UUID.randomUUID().toString())
                        .transactionId(transactionId.toString())
                        .providerRefundId(paypalResponse.getId())
                        .status(mapPayPalRefundStatus(paypalResponse.getStatus()))
                        .amount(refundRequest.getAmount() != null ? refundRequest.getAmount() : transaction.getAmount())
                        .currency(transaction.getCurrency())
                        .paymentChannel(paymentChannel)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
            } else {
                log.error("Unsupported payment channel for refund: {}", paymentChannel);
                return RefundResponse.builder()
                        .status(RefundStatus.FAILED)
                        .transactionId(transactionId.toString())
                        .errorMessage("Unsupported payment channel: " + paymentChannel)
                        .build();
            }

            // Do not mark the transaction as REFUNDED here.
            // For both Stripe and PayPal we rely on provider webhooks to confirm refund completion
            // and update the transaction status asynchronously.

            // Set transaction ID in response
            refundResponse.setTransactionId(transactionId.toString());

            return refundResponse;

        } catch (Exception e) {
            log.error("Error processing refund for transaction {}: {}", transactionId, e.getMessage(), e);
            return RefundResponse.builder()
                    .status(RefundStatus.FAILED)
                    .transactionId(transactionId.toString())
                    .errorMessage("Refund processing failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Maps PayPal refund status to internal RefundStatus.
     */
    private RefundStatus mapPayPalRefundStatus(String paypalStatus) {
        if (paypalStatus == null) {
            return RefundStatus.PENDING;
        }
        switch (paypalStatus.toUpperCase()) {
            case "COMPLETED":
                return RefundStatus.COMPLETED;
            case "PENDING":
                return RefundStatus.PENDING;
            case "FAILED":
                return RefundStatus.FAILED;
            case "CANCELLED":
                return RefundStatus.CANCELLED;
            default:
                return RefundStatus.PROCESSING;
        }
    }
}
