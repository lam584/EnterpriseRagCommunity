package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicConfigurationLoaderTest {

    @Test
    void refreshEnvironment_when_empty_configs_should_return_without_changes() {
        ConfigurableEnvironment env = new StandardEnvironment();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getAllConfigs()).thenReturn(Map.of());

        DynamicConfigurationLoader loader = new DynamicConfigurationLoader(env, systemConfigurationService);

        loader.refreshEnvironment();

        assertFalse(env.getPropertySources().contains("db-system-configurations"));
    }

    @Test
    void refreshEnvironment_should_add_then_replace_property_source() {
        ConfigurableEnvironment env = new StandardEnvironment();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);

        DynamicConfigurationLoader loader = new DynamicConfigurationLoader(env, systemConfigurationService);

        when(systemConfigurationService.getAllConfigs()).thenReturn(Map.of("k", "v1"));
        loader.refreshEnvironment();
        assertTrue(env.getPropertySources().contains("db-system-configurations"));
        assertEquals("v1", env.getProperty("k"));

        when(systemConfigurationService.getAllConfigs()).thenReturn(Map.of("k", "v2"));
        loader.refreshEnvironment();
        assertTrue(env.getPropertySources().contains("db-system-configurations"));
        assertEquals("v2", env.getProperty("k"));
    }
}

