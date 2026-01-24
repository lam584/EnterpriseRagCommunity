package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.security.totp")
public class TotpSecurityProperties {
    /**
     * Base64 encoded AES master key. Recommended 32 bytes (AES-256).
     * Keep empty in dev if you don't use TOTP, but required for enrollment.
     */
    private String masterKey;
}

