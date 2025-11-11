package com.example.EnterpriseRagCommunity.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {
        "com.example.enterpriseragcommunity.repository"
})
public class JpaRepositoriesConfig {
}
