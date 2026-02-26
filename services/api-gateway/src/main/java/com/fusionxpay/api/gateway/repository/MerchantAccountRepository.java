package com.fusionxpay.api.gateway.repository;

import com.fusionxpay.api.gateway.model.MerchantAccount;
import com.fusionxpay.api.gateway.model.MerchantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantAccountRepository extends JpaRepository<MerchantAccount, Long> {

    Optional<MerchantAccount> findByIdAndStatus(Long id, MerchantStatus status);

    Optional<MerchantAccount> findByEmailAndStatus(String email, MerchantStatus status);
}
