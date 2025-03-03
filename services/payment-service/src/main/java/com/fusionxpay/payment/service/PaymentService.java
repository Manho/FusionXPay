package com.fusionxpay.payment.service;

import com.fusionxpay.payment.dto.PaymentCallbackRequest;
import com.fusionxpay.payment.dto.PaymentRequest;
import com.fusionxpay.payment.dto.PaymentResponse;
import com.fusionxpay.payment.event.OrderEventProducer;
import com.fusionxpay.payment.model.PaymentStatus;
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
                return mapTransactionToResponse(transaction, null);
            }
            
            // If the transaction is in PROCESSING state, return it
            if (PaymentStatus.PROCESSING.name().equals(transaction.getStatus())) {
                log.info("Payment for order {} is already processing", paymentRequest.getOrderId());
                return mapTransactionToResponse(transaction, null);
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
        
        // Process the payment with the provider
        PaymentResponse providerResponse = provider.processPayment(paymentRequest);
        
        // Update the transaction status based on the provider response
        transaction.setStatus(providerResponse.getStatus().name());
        transaction = paymentTransactionRepository.save(transaction);
        
        // Notify the order service about the payment initiation
        orderEventProducer.sendPaymentStatusUpdate(
                transaction.getOrderId(), 
                transaction.getTransactionId(), 
                PaymentStatus.valueOf(transaction.getStatus())
        );
        
        return mapTransactionToResponse(transaction, providerResponse.getRedirectUrl());
    }
    
    /**
     * Processes a payment callback from a payment provider
     * 
     * @param callbackRequest the callback request from the provider
     * @return true if the callback was processed successfully
     */
    @Transactional
    public boolean processPaymentCallback(PaymentCallbackRequest callbackRequest) {
        log.info("Processing payment callback for transaction: {}", callbackRequest.getTransactionId());
        
        // Find the transaction
        Optional<PaymentTransaction> optionalTransaction = 
                paymentTransactionRepository.findById(callbackRequest.getTransactionId());
        
        if (optionalTransaction.isEmpty()) {
            log.error("Transaction not found: {}", callbackRequest.getTransactionId());
            return false;
        }
        
        PaymentTransaction transaction = optionalTransaction.get();
        
        // Get the payment provider
        PaymentProvider provider = paymentProviderFactory.getProvider(transaction.getPaymentChannel());
        
        // Validate the callback signature
        if (!provider.validateCallback(callbackRequest.toString(), callbackRequest.getSignature())) {
            log.error("Invalid callback signature for transaction: {}", callbackRequest.getTransactionId());
            return false;
        }
        
        // Update the transaction status
        PaymentStatus newStatus = PaymentStatus.valueOf(callbackRequest.getStatus());
        transaction.setStatus(newStatus.name());
        transaction = paymentTransactionRepository.save(transaction);
        log.info("Updated transaction status to {}: {}", newStatus, transaction.getTransactionId());
        
        // Notify the order service about the payment status update
        orderEventProducer.sendPaymentStatusUpdate(
                transaction.getOrderId(), 
                transaction.getTransactionId(), 
                newStatus
        );
        
        return true;
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
        
        return mapTransactionToResponse(optionalTransaction.get(), null);
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
        
        return mapTransactionToResponse(optionalTransaction.get(), null);
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
     * Maps a PaymentTransaction entity to a PaymentResponse DTO
     * 
     * @param transaction the transaction entity
     * @param redirectUrl the redirect URL from the provider
     * @return PaymentResponse DTO
     */
    private PaymentResponse mapTransactionToResponse(PaymentTransaction transaction, String redirectUrl) {
        return PaymentResponse.builder()
                .transactionId(transaction.getTransactionId())
                .orderId(transaction.getOrderId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentChannel(transaction.getPaymentChannel())
                .status(PaymentStatus.valueOf(transaction.getStatus()))
                .redirectUrl(redirectUrl)
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
}
