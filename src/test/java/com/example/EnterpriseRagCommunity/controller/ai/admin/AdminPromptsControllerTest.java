package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchRequest;
import com.example.EnterpriseRagCommunity.dto.ai.PromptBatchResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PromptContentUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.PromptsAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminPromptsControllerTest {

    @Test
    void batchGet_shouldRejectNullBody() {
        PromptsAdminService promptsAdminService = mock(PromptsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPromptsController controller = new AdminPromptsController(promptsAdminService, administratorService);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.batchGet(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void batchGet_shouldDelegateToService() {
        PromptsAdminService promptsAdminService = mock(PromptsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPromptsController controller = new AdminPromptsController(promptsAdminService, administratorService);

        PromptBatchRequest req = new PromptBatchRequest();
        req.setCodes(List.of("A", "B"));

        PromptBatchResponse ok = new PromptBatchResponse();
        when(promptsAdminService.batchGetByCodes(List.of("A", "B"))).thenReturn(ok);

        PromptBatchResponse got = controller.batchGet(req);
        assertSame(ok, got);
        verify(promptsAdminService).batchGetByCodes(List.of("A", "B"));
    }

    @Test
    void updateContent_shouldPassNullUserId_whenNoPrincipal() {
        PromptsAdminService promptsAdminService = mock(PromptsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPromptsController controller = new AdminPromptsController(promptsAdminService, administratorService);

        PromptContentUpdateRequest req = new PromptContentUpdateRequest();
        PromptContentDTO ok = new PromptContentDTO();
        when(promptsAdminService.updateContent("P1", req, null)).thenReturn(ok);

        PromptContentDTO got = controller.updateContent("P1", req, null);
        assertSame(ok, got);
        verify(promptsAdminService).updateContent("P1", req, null);
    }

    @Test
    void updateContent_shouldResolveUserId_whenPrincipalPresent() {
        PromptsAdminService promptsAdminService = mock(PromptsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPromptsController controller = new AdminPromptsController(promptsAdminService, administratorService);

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));

        PromptContentUpdateRequest req = new PromptContentUpdateRequest();
        PromptContentDTO ok = new PromptContentDTO();
        when(promptsAdminService.updateContent("P1", req, 7L)).thenReturn(ok);

        Principal principal = () -> "u";
        PromptContentDTO got = controller.updateContent("P1", req, principal);
        assertSame(ok, got);
        verify(promptsAdminService).updateContent("P1", req, 7L);
    }

    @Test
    void updateContent_shouldMapNoSuchElementToNotFound() {
        PromptsAdminService promptsAdminService = mock(PromptsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminPromptsController controller = new AdminPromptsController(promptsAdminService, administratorService);

        PromptContentUpdateRequest req = new PromptContentUpdateRequest();
        when(promptsAdminService.updateContent("P404", req, null)).thenThrow(new java.util.NoSuchElementException("missing"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.updateContent("P404", req, null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("missing", ex.getReason());
    }
}

