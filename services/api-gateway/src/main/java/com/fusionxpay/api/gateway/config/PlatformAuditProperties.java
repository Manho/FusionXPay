package com.fusionxpay.api.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.platform.audit")
public class PlatformAuditProperties {

    private boolean enabled = true;
    private String topic = "platform-audit-log";
    private boolean reliable = false;
    private Duration sendTimeout = Duration.ofMillis(1_500);
}
