package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchAuthConfigValidatorTest {

    @Test
    void validate_should_handle_missing_api_key() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("   ");

        ElasticsearchAuthConfigValidator v = new ElasticsearchAuthConfigValidator(systemConfigurationService);
        v.validate();
    }

    @Test
    void validate_should_handle_present_api_key() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");

        ElasticsearchAuthConfigValidator v = new ElasticsearchAuthConfigValidator(systemConfigurationService);
        v.validate();
    }
}

