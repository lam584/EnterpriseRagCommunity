package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmPriceConfigUpsertRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.LlmPriceConfigAdminService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminLlmPriceConfigsControllerTest {

    @Test
    void upsert_shouldUseNullUserId_whenPrincipalNull() {
        LlmPriceConfigAdminService llmPriceConfigAdminService = mock(LlmPriceConfigAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminLlmPriceConfigsController controller = new AdminLlmPriceConfigsController(llmPriceConfigAdminService, administratorService);

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        AdminLlmPriceConfigDTO ok = new AdminLlmPriceConfigDTO();
        when(llmPriceConfigAdminService.upsert(req, null)).thenReturn(ok);

        AdminLlmPriceConfigDTO got = controller.upsert(req, null);

        assertSame(ok, got);
        verify(llmPriceConfigAdminService).upsert(req, null);
    }

    @Test
    void upsert_shouldResolveUserId_whenUserExists() {
        LlmPriceConfigAdminService llmPriceConfigAdminService = mock(LlmPriceConfigAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminLlmPriceConfigsController controller = new AdminLlmPriceConfigsController(llmPriceConfigAdminService, administratorService);

        UsersEntity u = new UsersEntity();
        u.setId(11L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        AdminLlmPriceConfigDTO ok = new AdminLlmPriceConfigDTO();
        when(llmPriceConfigAdminService.upsert(req, 11L)).thenReturn(ok);

        Principal principal = () -> "u";
        AdminLlmPriceConfigDTO got = controller.upsert(req, principal);

        assertSame(ok, got);
        verify(llmPriceConfigAdminService).upsert(req, 11L);
    }

    @Test
    void upsert_shouldUseNullUserId_whenUserNotFound() {
        LlmPriceConfigAdminService llmPriceConfigAdminService = mock(LlmPriceConfigAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminLlmPriceConfigsController controller = new AdminLlmPriceConfigsController(llmPriceConfigAdminService, administratorService);

        when(administratorService.findByUsername("u")).thenReturn(Optional.empty());

        AdminLlmPriceConfigUpsertRequest req = new AdminLlmPriceConfigUpsertRequest();
        AdminLlmPriceConfigDTO ok = new AdminLlmPriceConfigDTO();
        when(llmPriceConfigAdminService.upsert(req, null)).thenReturn(ok);

        Principal principal = () -> "u";
        AdminLlmPriceConfigDTO got = controller.upsert(req, principal);

        assertSame(ok, got);
        verify(llmPriceConfigAdminService).upsert(req, null);
    }
}

