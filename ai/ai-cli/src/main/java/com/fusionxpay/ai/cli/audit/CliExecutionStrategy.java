package com.fusionxpay.ai.cli.audit;

import com.fusionxpay.ai.common.audit.AuditRequestMetadata;
import com.fusionxpay.ai.common.audit.AuditRequestMetadataProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CliExecutionStrategy implements CommandLine.IExecutionStrategy {

    private static final String SOURCE = "CLI-Java";

    private final AuditRequestMetadataProvider auditRequestMetadataProvider;

    @Override
    public int execute(ParseResult parseResult) throws CommandLine.ExecutionException, CommandLine.ParameterException {
        Object command = parseResult.asCommandLineList().get(parseResult.asCommandLineList().size() - 1).getCommand();
        String actionName = resolveActionName(parseResult, command);
        try (AuditRequestMetadataProvider.Scope ignored = auditRequestMetadataProvider.withMetadata(AuditRequestMetadata.builder()
                .source(SOURCE)
                .actionName(actionName)
                .correlationId(UUID.randomUUID().toString())
                .build())) {
            return new CommandLine.RunLast().execute(parseResult);
        }
    }

    private String resolveActionName(ParseResult parseResult, Object command) {
        if (command instanceof CliAuditableCommand auditableCommand) {
            return auditableCommand.auditActionName();
        }
        return parseResult.asCommandLineList().stream()
                .map(line -> line.getCommandName())
                .collect(Collectors.joining("."));
    }
}
