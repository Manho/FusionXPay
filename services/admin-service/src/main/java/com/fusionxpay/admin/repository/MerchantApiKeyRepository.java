package com.fusionxpay.admin.repository;

import com.fusionxpay.admin.model.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {

    Optional<MerchantApiKey> findByMerchantIdAndActiveTrue(Long merchantId);

    Optional<MerchantApiKey> findByKeyHashAndActiveTrue(String keyHash);

    List<MerchantApiKey> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
