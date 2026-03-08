package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.AdminAiModelProbeResultDTO;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.AdminAiModelProbeService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAiModelProbeController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminAiModelProbeControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminAiModelProbeService service;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

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
    void probe_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai/models/probe")
                        .queryParam("kind", "chat")
                        .queryParam("providerId", "p1")
                        .queryParam("modelName", "m1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void probe_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ai/models/probe")
                        .queryParam("kind", "chat")
                        .queryParam("providerId", "p1")
                        .queryParam("modelName", "m1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void probe_shouldReturn200_whenOk_withoutTimeout() throws Exception {
        AdminAiModelProbeResultDTO dto = new AdminAiModelProbeResultDTO();
        dto.setOk(true);
        when(service.probe(eq("chat"), eq("p1"), eq("m1"), eq(null))).thenReturn(dto);

        mockMvc.perform(get("/api/admin/ai/models/probe")
                        .queryParam("kind", "chat")
                        .queryParam("providerId", "p1")
                        .queryParam("modelName", "m1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void probe_shouldReturn200_whenOk_withTimeout() throws Exception {
        AdminAiModelProbeResultDTO dto = new AdminAiModelProbeResultDTO();
        dto.setOk(true);
        when(service.probe(eq("chat"), eq("p1"), eq("m1"), eq(123L))).thenReturn(dto);

        mockMvc.perform(get("/api/admin/ai/models/probe")
                        .queryParam("kind", "chat")
                        .queryParam("providerId", "p1")
                        .queryParam("modelName", "m1")
                        .queryParam("timeoutMs", "123")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}

