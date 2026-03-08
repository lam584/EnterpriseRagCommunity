package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.PortalChatConfigService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminPortalChatConfigController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminPortalChatConfigControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortalChatConfigService portalChatConfigService;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());
    }

    @Test
    void getConfig_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai/portal-chat/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConfig_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ai/portal-chat/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getConfig_shouldReturn200_whenOk() throws Exception {
        when(portalChatConfigService.getAdminConfig()).thenReturn(new PortalChatConfigDTO());

        mockMvc.perform(get("/api/admin/ai/portal-chat/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk());
    }

    @Test
    void updateConfig_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/ai/portal-chat/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_shouldReturn200_whenOk() throws Exception {
        when(portalChatConfigService.upsertAdminConfig(any(PortalChatConfigDTO.class))).thenReturn(new PortalChatConfigDTO());

        mockMvc.perform(put("/api/admin/ai/portal-chat/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}

