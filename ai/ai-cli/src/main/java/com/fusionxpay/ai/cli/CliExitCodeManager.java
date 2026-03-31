package com.fusionxpay.ai.cli;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

@Component
public class CliExitCodeManager implements ExitCodeGenerator {

    private volatile int exitCode = 0;

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
