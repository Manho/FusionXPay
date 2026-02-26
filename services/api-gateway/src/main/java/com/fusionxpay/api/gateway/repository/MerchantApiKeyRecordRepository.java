package com.fusionxpay.api.gateway.repository;

import com.fusionxpay.api.gateway.model.MerchantApiKeyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MerchantApiKeyRecordRepository extends JpaRepository<MerchantApiKeyRecord, Long> {

    Optional<MerchantApiKeyRecord> findByKeyHashAndActiveTrue(String keyHash);
}
