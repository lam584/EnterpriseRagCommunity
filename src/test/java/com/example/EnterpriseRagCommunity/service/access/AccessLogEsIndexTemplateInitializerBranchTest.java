package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AccessLogEsIndexTemplateInitializerBranchTest {

    @Test
    void buildTemplateBody_should_include_client_ip_text_keyword_mapping() {
    AccessLogEsIndexProvisioningService initializer = new AccessLogEsIndexProvisioningService(
                mock(SystemConfigurationService.class),
                new ObjectMapper()
        );

        @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
        initializer,
        "buildTemplateBody",
        "access-logs-v1*",
        1,
        1
    );
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) body.get("template");
        @SuppressWarnings("unchecked")
        Map<String, Object> mappings = (Map<String, Object>) template.get("mappings");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> clientIpText = (Map<String, Object>) properties.get("client_ip_text");

        assertNotNull(clientIpText);
        assertEquals("keyword", clientIpText.get("type"));
    }

    @Test
    void buildCurrentIndexMappingBody_should_include_client_ip_text_keyword_mapping() {
        AccessLogEsIndexProvisioningService initializer = new AccessLogEsIndexProvisioningService(
                mock(SystemConfigurationService.class),
                new ObjectMapper()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(initializer, "buildCurrentIndexMappingBody");
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) body.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> clientIpText = (Map<String, Object>) properties.get("client_ip_text");

        assertNotNull(clientIpText);
        assertEquals("keyword", clientIpText.get("type"));
    }
}