package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import com.example.EnterpriseRagCommunity.service.ai.admin.ChatContextEventLogsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminChatContextGovernanceControllerTest {

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listLogs_shouldParseTimesToLocalDateTimeOrNull() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        ChatContextEventLogsService logsService = mock(ChatContextEventLogsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminChatContextGovernanceController controller =
                new AdminChatContextGovernanceController(configService, logsService, auditLogWriter, auditDiffBuilder);

        Page<?> page = new PageImpl<>(List.of());
        when(logsService.list(any(), any(), anyInt(), anyInt())).thenReturn((Page) page);

        controller.listLogs(0, 20, null, null);
        verify(logsService, times(1)).list(eq(null), eq(null), eq(0), eq(20));

        controller.listLogs(0, 20, "   ", "\n");
        verify(logsService, times(2)).list(eq(null), eq(null), eq(0), eq(20));

        controller.listLogs(0, 20, "not-a-time", "2026-02-27T01:02:03");
        verify(logsService, times(1)).list(eq(null), eq(LocalDateTime.parse("2026-02-27T01:02:03")), eq(0), eq(20));
    }

    @Test
    void updateConfig_shouldWriteAuditWithActorName_whenAuthenticated() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        ChatContextEventLogsService logsService = mock(ChatContextEventLogsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminChatContextGovernanceController controller =
                new AdminChatContextGovernanceController(configService, logsService, auditLogWriter, auditDiffBuilder);

        ChatContextGovernanceConfigDTO before = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO after = new ChatContextGovernanceConfigDTO();

        when(configService.getConfig()).thenReturn(before);
        when(configService.updateConfig(payload)).thenReturn(after);
        when(auditDiffBuilder.build(before, after)).thenReturn(Map.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  alice  ", "N/A", List.of())
        );

        ChatContextGovernanceConfigDTO got = controller.updateConfig(payload);
        assertSame(after, got);

        ArgumentCaptor<String> actorName = ArgumentCaptor.forClass(String.class);
        verify(auditLogWriter).write(
                eq(null),
                actorName.capture(),
                eq("ADMIN_SETTINGS_UPDATE"),
                eq("CHAT_CONTEXT_GOVERNANCE_CONFIG"),
                eq(null),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("更新对话上下文治理配置"),
                eq(null),
                eq(Map.of())
        );
        assertEquals("alice", actorName.getValue());
    }

    @Test
    void updateConfig_shouldWriteAuditWithNullActorName_whenAnonymous() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        ChatContextEventLogsService logsService = mock(ChatContextEventLogsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminChatContextGovernanceController controller =
                new AdminChatContextGovernanceController(configService, logsService, auditLogWriter, auditDiffBuilder);

        ChatContextGovernanceConfigDTO before = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO after = new ChatContextGovernanceConfigDTO();

        when(configService.getConfig()).thenReturn(before);
        when(configService.updateConfig(payload)).thenReturn(after);
        when(auditDiffBuilder.build(before, after)).thenReturn(Map.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "N/A", List.of())
        );

        controller.updateConfig(payload);

        verify(auditLogWriter).write(
                eq(null),
                eq(null),
                eq("ADMIN_SETTINGS_UPDATE"),
                eq("CHAT_CONTEXT_GOVERNANCE_CONFIG"),
                eq(null),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("更新对话上下文治理配置"),
                eq(null),
                eq(Map.of())
        );
    }
}
