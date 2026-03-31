package com.fusionxpay.ai.cli.audit;

import com.fusionxpay.ai.common.audit.AuditStatus;

public interface CliAuditableCommand {

    String auditActionName();

    String auditInputSummary();

    String auditOutputSummary();

    default AuditStatus auditStatus() {
        return AuditStatus.SUCCESS;
    }
}
