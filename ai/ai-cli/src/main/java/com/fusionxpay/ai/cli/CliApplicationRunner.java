package com.fusionxpay.ai.cli;

import com.fusionxpay.ai.cli.command.AuthCommand;
import com.fusionxpay.ai.cli.command.OrderCommand;
import com.fusionxpay.ai.cli.command.PaymentCommand;
import com.fusionxpay.ai.cli.command.RootCommand;
import com.fusionxpay.ai.cli.config.CliProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CliProperties.class)
public class CliApplicationRunner implements ApplicationRunner {

    private final RootCommand rootCommand;
    private final AuthCommand authCommand;
    private final PaymentCommand paymentCommand;
    private final OrderCommand orderCommand;
    private final CliProperties cliProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!cliProperties.isRunnerEnabled()) {
            return;
        }

        CommandLine commandLine = new CommandLine(rootCommand);
        commandLine.addSubcommand(authCommand);
        commandLine.addSubcommand(paymentCommand);
        commandLine.addSubcommand(orderCommand);

        int exitCode = commandLine.execute(args.getSourceArgs());
        if (exitCode != 0) {
            throw new CliExitException(exitCode);
        }
    }

    private static final class CliExitException extends RuntimeException implements ExitCodeGenerator {
        private final int exitCode;

        private CliExitException(int exitCode) {
            this.exitCode = exitCode;
        }

        @Override
        public int getExitCode() {
            return exitCode;
        }
    }
}
