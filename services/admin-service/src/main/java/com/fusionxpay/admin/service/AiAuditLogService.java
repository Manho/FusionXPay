package com.fusionxpay.admin.service;

import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.admin.model.AiAuditLog;
import com.fusionxpay.admin.repository.AiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAuditLogService {

    private final AiAuditLogRepository repository;

    @Transactional
    public void persist(AuditEvent event) {
        if (repository.existsByEventId(event.getEventId())) {
            log.info("Ignoring duplicate AI audit event {}", event.getEventId());
            return;
        }

        AiAuditLog logEntry = AiAuditLog.builder()
                .eventId(event.getEventId())
                .source(event.getSource())
                .merchantId(event.getMerchantId())
                .actionName(event.getActionName())
                .status(event.getStatus())
                .durationMs(event.getDurationMs())
                .inputSummary(event.getInputSummary())
                .outputSummary(event.getOutputSummary())
                .conversationId(event.getConversationId())
                .correlationId(event.getCorrelationId())
                .createdAt(event.getTimestamp() == null ? Instant.now() : event.getTimestamp())
                .build();
        try {
            repository.saveAndFlush(logEntry);
        } catch (DataIntegrityViolationException ex) {
            if (!repository.existsByEventId(event.getEventId())) {
                throw ex;
            }
            log.info("Ignoring duplicate AI audit event {}", event.getEventId());
        }
    }
}
