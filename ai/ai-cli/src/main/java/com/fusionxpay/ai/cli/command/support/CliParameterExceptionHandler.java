package com.fusionxpay.ai.cli.command.support;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class CliParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {

    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) {
        CommandLine commandLine = ex.getCommandLine();
        commandLine.getErr().println("Error: " + ex.getMessage());
        commandLine.usage(commandLine.getErr());
        return CommandLine.ExitCode.USAGE;
    }
}
