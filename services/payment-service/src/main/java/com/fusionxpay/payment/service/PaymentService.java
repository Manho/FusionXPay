package com.fusionxpay.payment.service;

import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.common.model.PaymentStatus;
import com.fusionxpay.payment.model.PaymentTransaction;
import com.fusionxpay.payment.provider.PaymentProvider;
import com.fusionxpay.payment.provider.PaymentProviderFactory;
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
                log.error("Invalid callback response from provider: {}, missing Order ID", provider);
                return false;
            }
            
            // Find and update transaction record
            Optional<PaymentTransaction> optionalTransaction = 
                paymentTransactionRepository.findByOrderId(response.getOrderId());
            
            if (optionalTransaction.isEmpty()) {
                log.error("Transaction not found: {}", response.getOrderId());
                return false;
            }

            PaymentTransaction transaction = optionalTransaction.get();
            
            // Log status change
            log.info("Updating transaction {} status: {} -> {}", 
                transaction.getTransactionId(), transaction.getStatus(), response.getStatus().name());
                
            // Update transaction status
            transaction.setStatus(response.getStatus().name());
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
        return PaymentResponse.builder()
                .transactionId(transaction.getTransactionId())
                .orderId(transaction.getOrderId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentChannel(transaction.getPaymentChannel())
                .status(PaymentStatus.valueOf(transaction.getStatus()))
                .redirectUrl(redirectUrl)
                .errorMessage(errorMessage)
                .providerTransactionId(transaction.getProviderTransactionId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
