package com.fusionxpay.ai.mcpserver.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fusionxpay.ai.mcpserver.config.McpSafetyProperties;
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

import java.util.regex.Pattern;

@Aspect
@Component
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class OutputSafetyAspect {

    private static final String REDACTION = "[REDACTED]";

    private final ObjectMapper objectMapper;
    private final McpSafetyProperties safetyProperties;
    private final ObjectProvider<ToolAspectObserver> observerProvider;

    @Around("@annotation(com.fusionxpay.ai.mcpserver.tool.McpToolOperation)")
    public Object sanitize(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = toolName(joinPoint);
        observerProvider.ifAvailable(observer -> observer.onEnter("output", toolName));
        Object result = joinPoint.proceed();
        if (result == null) {
            return null;
        }

        if (result instanceof String value) {
            return redactString(value);
        }

        try {
            JsonNode node = objectMapper.valueToTree(result);
            JsonNode sanitized = sanitizeNode(node);
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            return objectMapper.treeToValue(sanitized, methodSignature.getReturnType());
        } catch (Exception ex) {
            log.debug("Failed to sanitize tool output for {}", toolName, ex);
            return result;
        }
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(redactString(node.asText()));
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node.deepCopy();
            for (int index = 0; index < arrayNode.size(); index++) {
                arrayNode.set(index, sanitizeNode(arrayNode.get(index)));
            }
            return arrayNode;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node.deepCopy();
            objectNode.fieldNames().forEachRemaining(field -> objectNode.set(field, sanitizeNode(objectNode.get(field))));
            return objectNode;
        }
        return node;
    }

    private String redactString(String value) {
        String sanitized = value;
        for (String pattern : safetyProperties.getRedactionPatterns()) {
            sanitized = Pattern.compile(pattern).matcher(sanitized).replaceAll(REDACTION);
        }
        return sanitized;
    }

    private String toolName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        McpToolOperation operation = signature.getMethod().getAnnotation(McpToolOperation.class);
        return operation == null ? signature.getMethod().getName() : operation.value();
    }
}
