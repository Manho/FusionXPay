package com.fusionxpay.admin.repository;

import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Merchant entity
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    /**
     * Find merchant by email
     */
    Optional<Merchant> findByEmail(String email);

    /**
     * Find merchant by merchant code
     */
    Optional<Merchant> findByMerchantCode(String merchantCode);

    /**
     * Find active merchant by email
     */
    Optional<Merchant> findByEmailAndStatus(String email, MerchantStatus status);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Check if merchant code exists
     */
    boolean existsByMerchantCode(String merchantCode);
}
