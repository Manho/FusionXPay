package com.fusionxpay.admin.repository;

import com.fusionxpay.admin.model.MerchantApiKeyAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantApiKeyAuditRepository extends JpaRepository<MerchantApiKeyAudit, Long> {
}
