package com.fusionxpay.ai.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.confirmation")
public class ConfirmationProperties {

    private Duration ttl = Duration.ofMinutes(10);
}
