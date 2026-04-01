package com.fusionxpay.admin.repository;

import com.fusionxpay.admin.model.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, Long> {

    Optional<AiAuditLog> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
