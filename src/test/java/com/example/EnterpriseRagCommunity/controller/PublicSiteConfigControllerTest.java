package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicSiteConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicSiteConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemConfigurationService systemConfigurationService;

    @Test
    void getSiteConfig_shouldReturnNulls_whenBeianTextMissing() throws Exception {
        when(systemConfigurationService.getConfig("APP_SITE_BEIAN")).thenReturn(null);
        when(systemConfigurationService.getConfig("APP_SITE_BEIAN_HREF")).thenReturn(null);

        mockMvc.perform(get("/api/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beianText").value(nullValue()))
                .andExpect(jsonPath("$.beianHref").value(nullValue()));
    }

    @Test
    void getSiteConfig_shouldDefaultHref_whenBeianTextPresentButHrefMissing() throws Exception {
        when(systemConfigurationService.getConfig("APP_SITE_BEIAN")).thenReturn("备案号");
        when(systemConfigurationService.getConfig("APP_SITE_BEIAN_HREF")).thenReturn(null);

        mockMvc.perform(get("/api/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beianText").value("备案号"))
                .andExpect(jsonPath("$.beianHref").value("https://beian.miit.gov.cn/"));
    }

    @Test
    void getSiteConfig_shouldUseConfiguredHref_whenProvided() throws Exception {
        when(systemConfigurationService.getConfig("APP_SITE_BEIAN")).thenReturn("备案号");
        when(systemConfigurationService.getConfig("APP_SITE_BEIAN_HREF")).thenReturn("https://example.invalid/");

        mockMvc.perform(get("/api/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beianText").value("备案号"))
                .andExpect(jsonPath("$.beianHref").value("https://example.invalid/"));
    }
}
