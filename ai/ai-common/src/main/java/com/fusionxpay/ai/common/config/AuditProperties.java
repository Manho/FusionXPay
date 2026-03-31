package com.fusionxpay.ai.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.audit")
public class AuditProperties {

    private boolean enabled = true;

    private String topic = "ai-audit-log";
}
