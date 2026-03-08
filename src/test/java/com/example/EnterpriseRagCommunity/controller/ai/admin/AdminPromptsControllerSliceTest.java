package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.PromptsAdminService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminPromptsController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminPromptsControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PromptsAdminService promptsAdminService;

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
    void batchGet_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/admin/prompts/batch").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batchGet_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(post("/api/admin/prompts/batch")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "access")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"c1\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchGet_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/prompts/batch")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"c1\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void batchGet_shouldReturn400_whenBodyNull() throws Exception {
        mockMvc.perform(post("/api/admin/prompts/batch")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchGet_shouldReturn200_whenOk() throws Exception {
        PromptContentDTO p = new PromptContentDTO();
        p.setPromptCode("c1");
        PromptBatchResponse resp = new PromptBatchResponse();
        resp.setPrompts(List.of(p));
        resp.setMissingCodes(List.of("c2"));
        when(promptsAdminService.batchGetByCodes(eq(List.of("c1", "c2")))).thenReturn(resp);

        mockMvc.perform(post("/api/admin/prompts/batch")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codes\":[\"c1\",\"c2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompts.length()").value(1))
                .andExpect(jsonPath("$.prompts[0].promptCode").value("c1"))
                .andExpect(jsonPath("$.missingCodes.length()").value(1))
                .andExpect(jsonPath("$.missingCodes[0]").value("c2"));
    }

    @Test
    void updateContent_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/prompts/{promptCode}/content", "c1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateContent_shouldReturn404_whenNotFound() throws Exception {
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());
        when(promptsAdminService.updateContent(eq("c1"), any(), eq(null))).thenThrow(new java.util.NoSuchElementException("not found"));

        mockMvc.perform(put("/api/admin/prompts/{promptCode}/content", "c1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateContent_shouldReturn200_whenOk() throws Exception {
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());
        PromptContentDTO dto = new PromptContentDTO();
        dto.setPromptCode("c1");
        when(promptsAdminService.updateContent(eq("c1"), any(), eq(null))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/prompts/{promptCode}/content", "c1")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptCode").value("c1"));
    }
}

