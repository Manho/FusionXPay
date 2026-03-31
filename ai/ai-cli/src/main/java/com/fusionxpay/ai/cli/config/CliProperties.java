package com.fusionxpay.ai.cli.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
@Setter
@ConfigurationProperties(prefix = "fusionx.ai.cli")
public class CliProperties {

    private boolean runnerEnabled = true;

    private Path configPath = Paths.get(System.getProperty("user.home"), ".fusionx", "config.json");
}
