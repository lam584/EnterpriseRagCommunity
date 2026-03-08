package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderAddModelRequestDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigUpsertRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchRequest;
import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentUpdateRequest;
import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.AiProviderModelsAdminService;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import com.example.EnterpriseRagCommunity.service.ai.LlmPriceConfigAdminService;
import com.example.EnterpriseRagCommunity.service.ai.PromptsAdminService;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
import com.example.EnterpriseRagCommunity.service.ai.admin.ChatContextEventLogsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiAdminControllersBranchIntegrationTest {

    @AfterEach
    void cleanupSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportedLanguagesController_branches() {
        SupportedLanguageService supportedLanguageService = mock(SupportedLanguageService.class);
        AdminAiSupportedLanguagesController controller = new AdminAiSupportedLanguagesController(supportedLanguageService);

        SupportedLanguageDTO payload = new SupportedLanguageDTO();
        payload.setLanguageCode("en");
        assertThrows(IllegalArgumentException.class, () -> controller.update(SupportedLanguageService.DEFAULT_LANGUAGE_CODE, payload));

        SupportedLanguageDTO payload2 = new SupportedLanguageDTO();
        payload2.setLanguageCode("  zh-CN  ");
        SupportedLanguageDTO ok = new SupportedLanguageDTO();
        when(supportedLanguageService.adminUpdate("zh-CN", payload2)).thenReturn(ok);
        assertSame(ok, controller.update("  zh-CN  ", payload2));

        SupportedLanguageDTO payload3 = new SupportedLanguageDTO();
        payload3.setLanguageCode("zh-CN");
        when(supportedLanguageService.adminUpdate("x", payload3)).thenThrow(new IllegalArgumentException("语言不存在: x"));
        assertThrows(ResourceNotFoundException.class, () -> controller.update("x", payload3));

        doThrow(new IllegalArgumentException("语言不存在: x")).when(supportedLanguageService).adminDeactivate("x");
        assertThrows(ResourceNotFoundException.class, () -> controller.delete("x"));
    }

    @Test
    void chatContextGovernanceController_branches() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        ChatContextEventLogsService logsService = mock(ChatContextEventLogsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminChatContextGovernanceController controller =
                new AdminChatContextGovernanceController(configService, logsService, auditLogWriter, auditDiffBuilder);

        Page<?> page = new PageImpl<>(List.of());
        when(logsService.list(any(), any(), anyInt(), anyInt())).thenReturn((Page) page);

        controller.listLogs(0, 20, "not-a-time", "2026-02-27T01:02:03");
        verify(logsService).list(eq(null), eq(LocalDateTime.parse("2026-02-27T01:02:03")), eq(0), eq(20));

        ChatContextGovernanceConfigDTO before = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO payload = new ChatContextGovernanceConfigDTO();
        ChatContextGovernanceConfigDTO after = new ChatContextGovernanceConfigDTO();
        when(configService.getConfig()).thenReturn(before);
        when(configService.updateConfig(payload)).thenReturn(after);
        when(auditDiffBuilder.build(before, after)).thenReturn(Map.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  alice  ", "N/A", List.of())
        );
        controller.updateConfig(payload);
        verify(auditLogWriter).write(eq(null), eq("alice"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aiProviderModelsController_branches() {
        AiProviderModelsAdminService aiProviderModelsAdminService = mock(AiProviderModelsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminAiProviderModelsController controller = new AdminAiProviderModelsController(aiProviderModelsAdminService, administratorService);

        AiProviderModelsDTO ok0 = new AiProviderModelsDTO();
        when(aiProviderModelsAdminService.addProviderModel("p1", null, null, null)).thenReturn(ok0);
        assertSame(ok0, controller.addModel("p1", null, null));

        UsersEntity u = new UsersEntity();
        u.setId(9L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));

        AiProviderAddModelRequestDTO payload = new AiProviderAddModelRequestDTO();
        payload.setPurpose("chat");
        payload.setModelName("m1");
        AiProviderModelsDTO ok1 = new AiProviderModelsDTO();
        when(aiProviderModelsAdminService.addProviderModel("p1", "chat", "m1", 9L)).thenReturn(ok1);
        Principal principal = () -> "u";
        assertSame(ok1, controller.addModel("p1", payload, principal));
    }

    @Test
    void promptsController_branches() {
        PromptsAdminService promptsAdminService = mock(PromptsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPromptsController controller = new AdminPromptsController(promptsAdminService, administratorService);

        ResponseStatusException bad = assertThrows(ResponseStatusException.class, () -> controller.batchGet(null));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());

        PromptBatchRequest req = new PromptBatchRequest();
        req.setCodes(List.of("A"));
        PromptBatchResponse ok = new PromptBatchResponse();
        when(promptsAdminService.batchGetByCodes(List.of("A"))).thenReturn(ok);
        assertSame(ok, controller.batchGet(req));

        PromptContentUpdateRequest ureq = new PromptContentUpdateRequest();
        when(promptsAdminService.updateContent("P404", ureq, null)).thenThrow(new java.util.NoSuchElementException("missing"));
        ResponseStatusException nf = assertThrows(ResponseStatusException.class, () -> controller.updateContent("P404", ureq, null));
        assertEquals(HttpStatus.NOT_FOUND, nf.getStatusCode());
    }

    @Test
    void llmPriceConfigsController_branches() {
        LlmPriceConfigAdminService llmPriceConfigAdminService = mock(LlmPriceConfigAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminLlmPriceConfigsController controller = new AdminLlmPriceConfigsController(llmPriceConfigAdminService, administratorService);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        AdminLlmPriceConfigDTO ok0 = new AdminLlmPriceConfigDTO();
        when(llmPriceConfigAdminService.upsert(req, null)).thenReturn(ok0);
        assertSame(ok0, controller.upsert(req, null));

        UsersEntity u = new UsersEntity();
        u.setId(11L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));
        AdminLlmPriceConfigDTO ok1 = new AdminLlmPriceConfigDTO();
        when(llmPriceConfigAdminService.upsert(req, 11L)).thenReturn(ok1);
        Principal principal = () -> "u";
        assertSame(ok1, controller.upsert(req, principal));
    }
}

