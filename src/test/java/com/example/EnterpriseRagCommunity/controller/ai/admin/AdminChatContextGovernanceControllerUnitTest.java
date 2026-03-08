package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import com.example.EnterpriseRagCommunity.service.ai.admin.ChatContextEventLogsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminChatContextGovernanceControllerUnitTest {

    @Test
    void updateConfig_shouldUseNullUsername_whenNoAuthentication() {
        ChatContextGovernanceConfigService cfg = mock(ChatContextGovernanceConfigService.class);
        ChatContextEventLogsService logs = mock(ChatContextEventLogsService.class);
        AuditLogWriter audit = mock(AuditLogWriter.class);
        AuditDiffBuilder diff = mock(AuditDiffBuilder.class);

        AdminChatContextGovernanceController c = new AdminChatContextGovernanceController(cfg, logs, audit, diff);

        ChatContextGovernanceConfigDTO before = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO after = new ChatContextGovernanceConfigDTO();
        when(cfg.getConfig()).thenReturn(before);
        when(cfg.updateConfig(any(ChatContextGovernanceConfigDTO.class))).thenReturn(after);
        when(diff.build(eq(before), eq(after))).thenReturn(Map.of());

        SecurityContextHolder.clearContext();
        c.updateConfig(new ChatContextGovernanceConfigDTO());

        verify(audit).write(
                Mockito.isNull(),
                Mockito.isNull(),
                eq("ADMIN_SETTINGS_UPDATE"),
                eq("CHAT_CONTEXT_GOVERNANCE_CONFIG"),
                Mockito.isNull(),
                eq(AuditResult.SUCCESS),
                eq("更新对话上下文治理配置"),
                Mockito.isNull(),
                eq(Map.of())
        );
    }

    @Test
    void updateConfig_shouldUseNullUsername_whenSecurityContextThrows() {
        ChatContextGovernanceConfigService cfg = mock(ChatContextGovernanceConfigService.class);
        ChatContextEventLogsService logs = mock(ChatContextEventLogsService.class);
        AuditLogWriter audit = mock(AuditLogWriter.class);
        AuditDiffBuilder diff = mock(AuditDiffBuilder.class);

        AdminChatContextGovernanceController c = new AdminChatContextGovernanceController(cfg, logs, audit, diff);

        ChatContextGovernanceConfigDTO before = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO after = new ChatContextGovernanceConfigDTO();
        when(cfg.getConfig()).thenReturn(before);
        when(cfg.updateConfig(any(ChatContextGovernanceConfigDTO.class))).thenReturn(after);
        when(diff.build(eq(before), eq(after))).thenReturn(Map.of());

        SecurityContext sc = new SecurityContext() {
            @Override
            public org.springframework.security.core.Authentication getAuthentication() {
                throw new RuntimeException("boom");
            }

            @Override
            public void setAuthentication(org.springframework.security.core.Authentication authentication) {
            }
        };
        SecurityContextHolder.setContext(sc);
        try {
            c.updateConfig(new ChatContextGovernanceConfigDTO());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(audit).write(
                Mockito.isNull(),
                Mockito.isNull(),
                eq("ADMIN_SETTINGS_UPDATE"),
                eq("CHAT_CONTEXT_GOVERNANCE_CONFIG"),
                Mockito.isNull(),
                eq(AuditResult.SUCCESS),
                eq("更新对话上下文治理配置"),
                Mockito.isNull(),
                eq(Map.of())
        );
    }
}

