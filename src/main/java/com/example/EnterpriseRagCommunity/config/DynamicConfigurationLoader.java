package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.Map;

@Configuration
public class DynamicConfigurationLoader {

    private final ConfigurableEnvironment environment;
    private final SystemConfigurationService systemConfigurationService;

    public DynamicConfigurationLoader(ConfigurableEnvironment environment, SystemConfigurationService systemConfigurationService) {
        this.environment = environment;
        this.systemConfigurationService = systemConfigurationService;
    }

    @PostConstruct
    public void loadConfigs() {
        refreshEnvironment();
    }

    public void refreshEnvironment() {
        Map<String, String> configs = systemConfigurationService.getAllConfigs();
        if (configs.isEmpty()) {
            return;
        }

        Map<String, Object> configMap = new java.util.HashMap<>(configs);
        PropertySource<?> propertySource = new MapPropertySource("db-system-configurations", configMap);
        
        MutablePropertySources sources = environment.getPropertySources();
        // Remove old if exists
        if (sources.contains("db-system-configurations")) {
            sources.replace("db-system-configurations", propertySource);
        } else {
            sources.addFirst(propertySource);
        }
    }
}
