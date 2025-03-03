package com.fusionxpay.payment.repository;

import com.fusionxpay.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByOrderId(UUID orderId);
}
