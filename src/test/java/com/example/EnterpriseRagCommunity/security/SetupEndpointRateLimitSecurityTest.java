package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.controller.AuthController;
import com.example.EnterpriseRagCommunity.controller.SetupController;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogEsIndexProvisioningService;
import com.example.EnterpriseRagCommunity.service.access.AccessLogKafkaLifecycleManager;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.utils.AesGcmUtils;
import com.example.EnterpriseRagCommunity.config.DynamicConfigurationLoader;
import com.example.EnterpriseRagCommunity.config.DynamicElasticsearchConfig;
import com.example.EnterpriseRagCommunity.config.ElasticsearchIndexStartupInitializer;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SetupController.class)
@Import({SecurityConfig.class, IpPathRateLimitFilter.class})
@TestPropertySource(properties = {
        "APP_MASTER_KEY=TEST_MASTER_KEY",
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.window-seconds=120",
        "app.security.rate-limit.setup-max-requests-per-window=1",
        "app.security.rate-limit.initial-admin-max-requests-per-window=5"
})
class SetupEndpointRateLimitSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministratorService administratorService;

    @MockitoBean
    private AccessControlService accessControlService;

    @MockitoBean
    private ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @MockitoBean
    private AdminSetupManager adminSetupManager;

    @MockitoBean
    private SystemConfigurationService systemConfigurationService;

    @MockitoBean
    private DynamicConfigurationLoader dynamicConfigurationLoader;

    @MockitoBean
    private DynamicElasticsearchConfig dynamicElasticsearchConfig;

    @MockitoBean
    private ElasticsearchIndexStartupInitializer elasticsearchIndexStartupInitializer;

    @MockitoBean
    private AuthController authController;

    @MockitoBean
    private InitialAdminIndexBootstrapService initialAdminIndexBootstrapService;

    @MockitoBean
    private AesGcmUtils aesGcmUtils;

    @MockitoBean
    private SetupController.RestClientFactory restClientFactory;

    @MockitoBean
    private AccessLogKafkaLifecycleManager accessLogKafkaLifecycleManager;

    @MockitoBean
    private AccessLogEsIndexProvisioningService accessLogEsIndexProvisioningService;

    @BeforeEach
    void setUp() {
        when(adminSetupManager.isSetupRequired()).thenReturn(true);
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        when(contentSafetyCircuitBreakerService.getConfig()).thenReturn(cfg);
    }

    @Test
    void setupEndpoint_shouldReturn429_onSecondRequestWithinWindow() throws Exception {
        mockMvc.perform(post("/api/setup/test-es")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/setup/test-es")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}

