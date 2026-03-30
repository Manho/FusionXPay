package com.fusionxpay.api.gateway.config;

import com.fusionxpay.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtUtils jwtUtils(@Value("${jwt.secret}") String jwtSecret) {
        return new JwtUtils(jwtSecret);
    }
}
