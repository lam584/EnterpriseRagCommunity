package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.AiProviderModelsAdminService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAiProviderModelsController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminAiProviderModelsControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiProviderModelsAdminService aiProviderModelsAdminService;

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
    void listModels_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers/{providerId}/models", "p1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listModels_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers/{providerId}/models", "p1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listModels_shouldReturn200_whenOk() throws Exception {
        AiProviderModelsDTO dto = new AiProviderModelsDTO();
        dto.setProviderId("p1");
        when(aiProviderModelsAdminService.listProviderModels("p1")).thenReturn(dto);

        mockMvc.perform(get("/api/admin/ai/providers/{providerId}/models", "p1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("p1"));
    }

    @Test
    void addModel_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/ai/providers/{providerId}/models", "p1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"chat\",\"modelName\":\"m\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addModel_shouldPassNulls_whenPayloadNull() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(20L);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(u));

        AiProviderModelsDTO dto = new AiProviderModelsDTO();
        dto.setProviderId("p1");
        when(aiProviderModelsAdminService.addProviderModel(eq("p1"), eq(null), eq(null), eq(20L))).thenReturn(dto);

        mockMvc.perform(post("/api/admin/ai/providers/{providerId}/models", "p1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("p1"));
    }

    @Test
    void addModel_shouldPassUserIdNull_whenUserNotFound() throws Exception {
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());

        AiProviderModelsDTO dto = new AiProviderModelsDTO();
        dto.setProviderId("p1");
        when(aiProviderModelsAdminService.addProviderModel(eq("p1"), eq("chat"), eq("m"), eq(null))).thenReturn(dto);

        mockMvc.perform(post("/api/admin/ai/providers/{providerId}/models", "p1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"chat\",\"modelName\":\"m\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("p1"));
    }

    @Test
    void deleteModel_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(delete("/api/admin/ai/providers/{providerId}/models", "p1")
                        .queryParam("purpose", "chat")
                        .queryParam("modelName", "m")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteModel_shouldReturn200_whenOk() throws Exception {
        AiProviderModelsDTO dto = new AiProviderModelsDTO();
        dto.setProviderId("p1");
        when(aiProviderModelsAdminService.deleteProviderModel("p1", "chat", "m")).thenReturn(dto);

        mockMvc.perform(delete("/api/admin/ai/providers/{providerId}/models", "p1")
                        .queryParam("purpose", "chat")
                        .queryParam("modelName", "m")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("p1"));
    }

    @Test
    void upstreamModels_shouldReturn200_whenOk() throws Exception {
        when(aiProviderModelsAdminService.fetchUpstreamModels("p1")).thenReturn(Map.of("providerId", "p1", "models", new String[]{"m"}));

        mockMvc.perform(get("/api/admin/ai/providers/{providerId}/upstream-models", "p1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerId").value("p1"));
    }

    @Test
    void previewUpstreamModels_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/ai/providers/upstream-models/preview")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewUpstreamModels_shouldReturn200_whenOk() throws Exception {
        when(aiProviderModelsAdminService.previewUpstreamModels(any())).thenReturn(Map.of("ok", true));

        mockMvc.perform(post("/api/admin/ai/providers/upstream-models/preview")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(aiProviderModelsAdminService).previewUpstreamModels(Mockito.any());
    }
}

