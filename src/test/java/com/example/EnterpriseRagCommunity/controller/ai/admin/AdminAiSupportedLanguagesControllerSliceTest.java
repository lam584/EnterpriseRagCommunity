package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAiSupportedLanguagesController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminAiSupportedLanguagesControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SupportedLanguageService supportedLanguageService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @BeforeEach
    void setUp() throws Exception {
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        when(contentSafetyCircuitBreakerService.getConfig()).thenReturn(cfg);

        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());
    }

    @Test
    void update_shouldReturn400_whenTryingToChangeDefaultLanguageCode() throws Exception {
        mockMvc.perform(put("/api/admin/ai/supported-languages/{code}", SupportedLanguageService.DEFAULT_LANGUAGE_CODE)
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_semantic_translate", "action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"en\",\"displayName\":\"English\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不允许修改默认语言代码"));
    }

    @Test
    void delete_shouldReturn400_whenTryingToDeleteDefaultLanguage() throws Exception {
        mockMvc.perform(delete("/api/admin/ai/supported-languages/{code}", SupportedLanguageService.DEFAULT_LANGUAGE_CODE)
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_semantic_translate", "action")))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不允许删除默认语言"));
    }

    @Test
    void update_shouldReturn404_whenLanguageNotFound() throws Exception {
        when(supportedLanguageService.adminUpdate(eq("xx"), any()))
                .thenThrow(new IllegalArgumentException("语言不存在: xx"));

        mockMvc.perform(put("/api/admin/ai/supported-languages/{code}", "xx")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_semantic_translate", "action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"xx\",\"displayName\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("语言不存在: xx"));
    }

    @Test
    void delete_shouldReturn404_whenLanguageNotFound() throws Exception {
        doThrow(new IllegalArgumentException("语言不存在: xx")).when(supportedLanguageService).adminDeactivate("xx");

        mockMvc.perform(delete("/api/admin/ai/supported-languages/{code}", "xx")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_semantic_translate", "action")))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("语言不存在: xx"));
    }

    @Test
    void update_shouldReturn400_forOtherIllegalArgument() throws Exception {
        when(supportedLanguageService.adminUpdate(eq("xx"), any()))
                .thenThrow(new IllegalArgumentException("languageCode 不能为空"));

        mockMvc.perform(put("/api/admin/ai/supported-languages/{code}", "xx")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_semantic_translate", "action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"xx\",\"displayName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("languageCode 不能为空"));
    }

    @Test
    void upsert_shouldReturn200_whenOk() throws Exception {
        SupportedLanguageDTO dto = new SupportedLanguageDTO();
        dto.setLanguageCode("en");
        dto.setDisplayName("English");
        when(supportedLanguageService.adminUpsert(any())).thenReturn(dto);

        mockMvc.perform(post("/api/admin/ai/supported-languages")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_semantic_translate", "action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"languageCode\":\"en\",\"displayName\":\"English\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languageCode").value("en"))
                .andExpect(jsonPath("$.displayName").value("English"));
    }
}

