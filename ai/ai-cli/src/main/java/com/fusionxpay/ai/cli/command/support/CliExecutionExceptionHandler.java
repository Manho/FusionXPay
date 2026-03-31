package com.fusionxpay.ai.cli.command.support;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class CliExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
        commandLine.getErr().println("Error: " + ex.getMessage());
        return 1;
    }
}
