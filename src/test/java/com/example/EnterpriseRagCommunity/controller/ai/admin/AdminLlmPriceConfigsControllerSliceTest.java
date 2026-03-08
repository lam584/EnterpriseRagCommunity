package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.LlmPriceConfigAdminService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminLlmPriceConfigsController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminLlmPriceConfigsControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LlmPriceConfigAdminService llmPriceConfigAdminService;

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
    void listAll_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai/prices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAll_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ai/prices")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_shouldReturn200_whenOk() throws Exception {
        AdminLlmPriceConfigDTO dto = new AdminLlmPriceConfigDTO();
        dto.setName("p1");
        when(llmPriceConfigAdminService.listAll()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/ai/prices")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("p1"));

        verify(llmPriceConfigAdminService).listAll();
    }

    @Test
    void upsert_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/ai/prices")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void upsert_shouldReturn400_whenValidationFails() throws Exception {
        mockMvc.perform(put("/api/admin/ai/prices")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void upsert_shouldReturn200_whenOk_withUserIdResolution() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(30L);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(u));

        AdminLlmPriceConfigDTO dto = new AdminLlmPriceConfigDTO();
        dto.setName("p1");
        when(llmPriceConfigAdminService.upsert(any(), eq(30L))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/ai/prices")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"p1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("p1"));

        verify(llmPriceConfigAdminService).upsert(any(), eq(30L));
    }
}

