package com.example.EnterpriseRagCommunity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {
    private String username;
    private String password;
    private String fromAddress;
    private String fromName;
}
