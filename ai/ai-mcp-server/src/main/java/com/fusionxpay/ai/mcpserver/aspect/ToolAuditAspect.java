package com.fusionxpay.ai.mcpserver.aspect;

import com.fusionxpay.ai.common.audit.AuditRequestMetadata;
import com.fusionxpay.ai.common.audit.AuditRequestMetadataProvider;
import com.fusionxpay.ai.mcpserver.tool.McpToolOperation;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class ToolAuditAspect {

    private static final String SOURCE = "MCP-Java";

    private final AuditRequestMetadataProvider auditRequestMetadataProvider;
    private final ObjectProvider<ToolAspectObserver> observerProvider;

    @Around("@annotation(com.fusionxpay.ai.mcpserver.tool.McpToolOperation)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = toolName(joinPoint);
        observerProvider.ifAvailable(observer -> observer.onEnter("audit", toolName));
        try (AuditRequestMetadataProvider.Scope ignored = auditRequestMetadataProvider.withMetadata(AuditRequestMetadata.builder()
                .source(SOURCE)
                .actionName(toolName)
                .correlationId(UUID.randomUUID().toString())
                .build())) {
            return joinPoint.proceed();
        }
    }

    private String toolName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        McpToolOperation operation = signature.getMethod().getAnnotation(McpToolOperation.class);
        return operation == null ? signature.getMethod().getName() : operation.value();
    }
}
