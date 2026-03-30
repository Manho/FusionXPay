package com.fusionxpay.payment.repository;

import com.fusionxpay.payment.model.PaymentTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByOrderId(UUID orderId);
    Optional<PaymentTransaction> findByOrderIdAndMerchantId(UUID orderId, Long merchantId);
    Optional<PaymentTransaction> findByTransactionIdAndMerchantId(UUID transactionId, Long merchantId);
    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE " +
            "pt.merchantId = :merchantId AND " +
            "(:status IS NULL OR pt.status = :status) AND " +
            "(:fromTime IS NULL OR pt.createdAt >= :fromTime) AND " +
            "(:toTime IS NULL OR pt.createdAt <= :toTime)")
    Page<PaymentTransaction> findWithFilters(
            @Param("merchantId") Long merchantId,
            @Param("status") String status,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            Pageable pageable);
}
