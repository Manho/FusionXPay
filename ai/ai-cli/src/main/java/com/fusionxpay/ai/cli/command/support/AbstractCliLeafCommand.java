package com.fusionxpay.ai.cli.command.support;

import com.fusionxpay.ai.cli.audit.CliAuditableCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

public abstract class AbstractCliLeafCommand implements CliAuditableCommand {

    @Spec
    protected CommandSpec spec;

    private String auditOutputSummary = "";

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    protected void setAuditOutputSummary(String auditOutputSummary) {
        this.auditOutputSummary = auditOutputSummary;
    }

    @Override
    public String auditOutputSummary() {
        return auditOutputSummary;
    }
}
