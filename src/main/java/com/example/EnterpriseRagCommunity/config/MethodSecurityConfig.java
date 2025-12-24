package com.example.EnterpriseRagCommunity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables method-level security annotations like @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
