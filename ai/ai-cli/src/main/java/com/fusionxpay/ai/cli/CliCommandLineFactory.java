package com.fusionxpay.ai.cli;

import com.fusionxpay.ai.cli.audit.CliExecutionStrategy;
import com.fusionxpay.ai.cli.command.RootCommand;
import com.fusionxpay.ai.cli.command.support.CliExecutionExceptionHandler;
import com.fusionxpay.ai.cli.command.support.CliParameterExceptionHandler;
import com.fusionxpay.ai.cli.command.support.SpringCommandFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@RequiredArgsConstructor
public class CliCommandLineFactory {

    private final RootCommand rootCommand;
    private final SpringCommandFactory springCommandFactory;
    private final CliExecutionStrategy executionStrategy;
    private final CliExecutionExceptionHandler executionExceptionHandler;
    private final CliParameterExceptionHandler parameterExceptionHandler;

    public CommandLine create() {
        CommandLine commandLine = new CommandLine(rootCommand, springCommandFactory);
        commandLine.setExecutionStrategy(executionStrategy);
        commandLine.setExecutionExceptionHandler(executionExceptionHandler);
        commandLine.setParameterExceptionHandler(parameterExceptionHandler);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        return commandLine;
    }
}
