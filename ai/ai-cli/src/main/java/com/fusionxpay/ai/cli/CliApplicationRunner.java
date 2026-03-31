package com.fusionxpay.ai.cli;

import com.fusionxpay.ai.cli.config.CliProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(CliProperties.class)
public class CliApplicationRunner implements ApplicationRunner {

    private final CliCommandLineFactory cliCommandLineFactory;
    private final CliProperties cliProperties;
    private final CliExitCodeManager cliExitCodeManager;

    @Override
    public void run(ApplicationArguments args) {
        if (!cliProperties.isRunnerEnabled()) {
            return;
        }

        int exitCode = cliCommandLineFactory.create().execute(args.getSourceArgs());
        cliExitCodeManager.setExitCode(exitCode);
    }
}
