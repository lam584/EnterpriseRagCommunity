package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentUpdateRequest;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PromptsAdminService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPromptsControllerUnitTest {

    @Test
    void updateContent_shouldAcceptNullPrincipal() {
        PromptsAdminService svc = mock(PromptsAdminService.class);
        AdministratorService adminSvc = mock(AdministratorService.class);
        AdminPromptsController c = new AdminPromptsController(svc, adminSvc);

        PromptContentUpdateRequest req = new PromptContentUpdateRequest();
        req.setName("n");

        PromptContentDTO dto = new PromptContentDTO();
        dto.setPromptCode("c1");
        when(svc.updateContent(eq("c1"), eq(req), eq(null))).thenReturn(dto);

        PromptContentDTO out = c.updateContent("c1", req, null);
        org.junit.jupiter.api.Assertions.assertEquals("c1", out.getPromptCode());
        verify(svc).updateContent(eq("c1"), eq(req), eq(null));
    }
}

