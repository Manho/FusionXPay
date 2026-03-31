package com.fusionxpay.ai.cli.audit;

import com.fusionxpay.ai.cli.config.CliConfigStore;
import com.fusionxpay.ai.cli.config.CliStoredConfig;
import com.fusionxpay.ai.common.audit.AuditEvent;
import com.fusionxpay.ai.common.audit.AuditEventPublisher;
import com.fusionxpay.ai.common.audit.AuditSource;
import com.fusionxpay.ai.common.audit.AuditStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CliExecutionStrategy implements CommandLine.IExecutionStrategy {

    private final AuditEventPublisher auditEventPublisher;
    private final CliConfigStore configStore;

    @Override
    public int execute(ParseResult parseResult) throws CommandLine.ExecutionException, CommandLine.ParameterException {
        Object command = parseResult.asCommandLineList().get(parseResult.asCommandLineList().size() - 1).getCommand();
        String actionName = resolveActionName(parseResult, command);
        String inputSummary = resolveInputSummary(parseResult, command);
        long startedAt = System.currentTimeMillis();
        int exitCode = 0;
        String outputSummary = null;
        AuditStatus status = AuditStatus.SUCCESS;

        try {
            exitCode = new CommandLine.RunLast().execute(parseResult);
            if (command instanceof CliAuditableCommand auditableCommand) {
                outputSummary = auditableCommand.auditOutputSummary();
                status = auditableCommand.auditStatus();
            }
            if (exitCode != 0) {
                status = AuditStatus.FAILURE;
                outputSummary = "exitCode=" + exitCode;
            }
            return exitCode;
        } catch (RuntimeException ex) {
            status = AuditStatus.FAILURE;
            outputSummary = ex.getMessage();
            throw ex;
        } finally {
            publishAuditEvent(actionName, inputSummary, outputSummary, status, startedAt);
        }
    }

    private void publishAuditEvent(String actionName,
                                   String inputSummary,
                                   String outputSummary,
                                   AuditStatus status,
                                   long startedAt) {
        CliStoredConfig config = configStore.load().orElse(null);
        auditEventPublisher.publish(AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .source(AuditSource.CLI)
                .merchantId(config == null ? null : config.getMerchantId())
                .actionName(actionName)
                .status(status)
                .durationMs(System.currentTimeMillis() - startedAt)
                .inputSummary(inputSummary)
                .outputSummary(defaultOutputSummary(outputSummary, status == AuditStatus.SUCCESS ? "OK" : "FAILED"))
                .timestamp(Instant.now())
                .conversationId(null)
                .correlationId(UUID.randomUUID().toString())
                .build());
    }

    private String resolveActionName(ParseResult parseResult, Object command) {
        if (command instanceof CliAuditableCommand auditableCommand) {
            return auditableCommand.auditActionName();
        }
        return parseResult.asCommandLineList().stream()
                .map(line -> line.getCommandName())
                .collect(Collectors.joining("."));
    }

    private String resolveInputSummary(ParseResult parseResult, Object command) {
        if (command instanceof CliAuditableCommand auditableCommand) {
            return auditableCommand.auditInputSummary();
        }
        return String.join(" ", parseResult.originalArgs());
    }

    private String defaultOutputSummary(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
