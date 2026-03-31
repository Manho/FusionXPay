package com.fusionxpay.ai.mcpserver.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.audit.AuditSource;
import com.fusionxpay.ai.common.audit.AuditStatus;
import com.fusionxpay.ai.common.dto.confirmation.ConfirmationResponse;
import com.fusionxpay.ai.mcpserver.config.McpSafetyProperties;
import com.fusionxpay.ai.mcpserver.tool.McpAuthenticationService;
import com.fusionxpay.ai.mcpserver.tool.McpToolOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class ToolAuditAspect {

    private final ObjectMapper objectMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final McpAuthenticationService authenticationService;
    private final McpSafetyProperties safetyProperties;
    private final ObjectProvider<ToolAspectObserver> observerProvider;

    @Around("@annotation(com.fusionxpay.ai.mcpserver.tool.McpToolOperation)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = toolName(joinPoint);
        observerProvider.ifAvailable(observer -> observer.onEnter("audit", toolName));
        long startedAt = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        Long merchantId = authenticationService.getCurrentMerchantIdOrNull();
        String inputSummary = summarize(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            merchantId = merchantId == null ? authenticationService.getCurrentMerchantIdOrNull() : merchantId;
            AuditStatus status = resolveStatus(result);
            publishAudit(toolName, merchantId, correlationId, inputSummary, summarize(result), startedAt, status);
            return result;
        } catch (Throwable ex) {
            merchantId = merchantId == null ? authenticationService.getCurrentMerchantIdOrNull() : merchantId;
            publishAudit(toolName, merchantId, correlationId, inputSummary, summarize(ex.getMessage()), startedAt, AuditStatus.FAILURE);
            throw ex;
        }
    }

    private AuditStatus resolveStatus(Object result) {
        if (result instanceof ConfirmationResponse confirmationResponse
                && confirmationResponse.getStatus() == com.fusionxpay.ai.common.dto.confirmation.ConfirmationStatus.CONFIRMATION_REQUIRED) {
            return AuditStatus.CONFIRMATION_REQUIRED;
        }
        return AuditStatus.SUCCESS;
    }

    private void publishAudit(String actionName,
                              Long merchantId,
                              String correlationId,
                              String inputSummary,
                              String outputSummary,
                              long startedAt,
                              AuditStatus status) {
        try {
            auditEventPublisher.publish(AuditEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .source(AuditSource.MCP)
                    .merchantId(merchantId)
                    .actionName(actionName)
                    .status(status)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .inputSummary(inputSummary)
                    .outputSummary(outputSummary)
                    .timestamp(Instant.now())
                    .correlationId(correlationId)
                    .build());
        } catch (Exception ex) {
            log.error("Failed to audit MCP tool {}", actionName, ex);
        }
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String summary = objectMapper.writeValueAsString(value);
            if (summary.length() <= safetyProperties.getMaxSummaryLength()) {
                return summary;
            }
            return summary.substring(0, safetyProperties.getMaxSummaryLength()) + "...";
        } catch (JsonProcessingException ex) {
            String summary = String.valueOf(value);
            if (summary.length() <= safetyProperties.getMaxSummaryLength()) {
                return summary;
            }
            return summary.substring(0, safetyProperties.getMaxSummaryLength()) + "...";
        }
    }

    private String toolName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        McpToolOperation operation = signature.getMethod().getAnnotation(McpToolOperation.class);
        return operation == null ? signature.getMethod().getName() : operation.value();
    }
}
