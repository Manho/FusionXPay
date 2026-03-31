package com.fusionxpay.ai.mcpserver.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusionxpay.ai.mcpserver.config.McpSafetyProperties;
import com.fusionxpay.ai.mcpserver.tool.McpToolOperation;
import com.fusionxpay.ai.mcpserver.tool.UnsafeToolInputException;
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

import java.util.regex.Pattern;

@Aspect
@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class InputSafetyAspect {

    private final ObjectMapper objectMapper;
    private final McpSafetyProperties safetyProperties;
    private final ObjectProvider<ToolAspectObserver> observerProvider;

    @Around("@annotation(com.fusionxpay.ai.mcpserver.tool.McpToolOperation)")
    public Object validate(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = toolName(joinPoint);
        observerProvider.ifAvailable(observer -> observer.onEnter("input", toolName));
        String payload = serialize(joinPoint.getArgs());
        if (payload.length() > safetyProperties.getMaxInputLength()) {
            throw blocked(toolName, "Input exceeds max allowed length");
        }
        if (containsBlockedPattern(payload)) {
            throw blocked(toolName, "Input matched a blocked safety pattern");
        }
        if (suspiciousCharacterRatio(payload) > safetyProperties.getSuspiciousCharacterRatio()) {
            throw blocked(toolName, "Input contains too many suspicious characters");
        }
        return joinPoint.proceed();
    }

    private UnsafeToolInputException blocked(String toolName, String reason) {
        log.warn("Blocked MCP tool {} due to unsafe input: {}", toolName, reason);
        return new UnsafeToolInputException(reason);
    }

    private boolean containsBlockedPattern(String payload) {
        return safetyProperties.getBlockedPatterns().stream()
                .map(Pattern::compile)
                .anyMatch(pattern -> pattern.matcher(payload).find());
    }

    private double suspiciousCharacterRatio(String payload) {
        if (payload.isBlank()) {
            return 0;
        }
        long suspiciousCharacters = payload.chars()
                .filter(ch -> !Character.isLetterOrDigit(ch)
                        && !Character.isWhitespace(ch)
                        && "{}[](),.:;_-/@".indexOf(ch) < 0)
                .count();
        return (double) suspiciousCharacters / (double) payload.length();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String toolName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        McpToolOperation operation = signature.getMethod().getAnnotation(McpToolOperation.class);
        return operation == null ? signature.getMethod().getName() : operation.value();
    }
}
