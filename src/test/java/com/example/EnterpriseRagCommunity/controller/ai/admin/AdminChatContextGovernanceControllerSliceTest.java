package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventDetailDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminChatContextEventLogDTO;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import com.example.EnterpriseRagCommunity.service.ai.admin.ChatContextEventLogsService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminChatContextGovernanceController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminChatContextGovernanceControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatContextGovernanceConfigService configService;

    @MockitoBean
    ChatContextEventLogsService logsService;

    @MockitoBean
    AuditLogWriter auditLogWriter;

    @MockitoBean
    AuditDiffBuilder auditDiffBuilder;

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
        mockMvc.perform(get("/api/admin/ai/chat-context/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConfig_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ai/chat-context/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getConfig_shouldReturn200_whenOk() throws Exception {
        ChatContextGovernanceConfigDTO dto = new ChatContextGovernanceConfigDTO();
        dto.setEnabled(true);
        when(configService.getConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/admin/ai/chat-context/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "access"))))
                .andExpect(status().isOk());
    }

    @Test
    void listLogs_shouldParseTimeOrNull() throws Exception {
        Page<AdminChatContextEventLogDTO> page = new PageImpl<>(List.of());
        when(logsService.list(eq(null), eq(null), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/admin/ai/chat-context/logs")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "access"))))
                .andExpect(status().isOk());

        verify(logsService).list(eq(null), eq(null), eq(0), eq(20));
    }

    @Test
    void listLogs_shouldTreatBlankAndInvalidAsNull() throws Exception {
        Page<AdminChatContextEventLogDTO> page = new PageImpl<>(List.of());
        when(logsService.list(eq(null), eq(null), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/admin/ai/chat-context/logs")
                        .queryParam("from", "  ")
                        .queryParam("to", "not-a-time")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "access"))))
                .andExpect(status().isOk());

        verify(logsService).list(eq(null), eq(null), eq(0), eq(20));
    }

    @Test
    void listLogs_shouldParseValidTimes() throws Exception {
        LocalDateTime from = LocalDateTime.parse("2024-01-02T03:04:05");
        LocalDateTime to = LocalDateTime.parse("2024-01-03T03:04:05");
        Page<AdminChatContextEventLogDTO> page = new PageImpl<>(List.of());
        when(logsService.list(eq(from), eq(to), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/admin/ai/chat-context/logs")
                        .queryParam("from", "2024-01-02T03:04:05")
                        .queryParam("to", "2024-01-03T03:04:05")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "access"))))
                .andExpect(status().isOk());

        verify(logsService).list(eq(from), eq(to), eq(0), eq(20));
    }

    @Test
    void getLog_shouldReturn200_whenOk() throws Exception {
        AdminChatContextEventDetailDTO dto = new AdminChatContextEventDetailDTO();
        dto.setId(1L);
        when(logsService.get(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/ai/chat-context/logs/{id}", 1)
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "access"))))
                .andExpect(status().isOk());
    }

    @Test
    void updateConfig_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/ai/chat-context/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_shouldWriteAuditLog() throws Exception {
        ChatContextGovernanceConfigDTO before = new ChatContextGovernanceConfigDTO();
        before.setEnabled(false);
        ChatContextGovernanceConfigDTO after = new ChatContextGovernanceConfigDTO();
        after.setEnabled(true);

        when(configService.getConfig()).thenReturn(before);
        when(configService.updateConfig(any(ChatContextGovernanceConfigDTO.class))).thenReturn(after);
        when(auditDiffBuilder.build(eq(before), eq(after))).thenReturn(Map.of("enabled", true));

        mockMvc.perform(put("/api/admin/ai/chat-context/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_ai_chat_context", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(auditLogWriter).write(
                Mockito.isNull(),
                eq("u@example.com"),
                eq("ADMIN_SETTINGS_UPDATE"),
                eq("CHAT_CONTEXT_GOVERNANCE_CONFIG"),
                Mockito.isNull(),
                eq(AuditResult.SUCCESS),
                eq("更新对话上下文治理配置"),
                Mockito.isNull(),
                eq(Map.of("enabled", true))
        );
    }
}

