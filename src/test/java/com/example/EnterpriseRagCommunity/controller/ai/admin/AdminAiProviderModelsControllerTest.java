package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderAddModelRequestDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiProviderModelsAdminService;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAiProviderModelsControllerTest {

    @Test
    void addModel_shouldPassNulls_whenNoPrincipalAndNoPayload() {
        AiProviderModelsAdminService aiProviderModelsAdminService = mock(AiProviderModelsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminAiProviderModelsController controller = new AdminAiProviderModelsController(aiProviderModelsAdminService, administratorService);

        AiProviderModelsDTO ok = new AiProviderModelsDTO();
        when(aiProviderModelsAdminService.addProviderModel("p1", null, null, null)).thenReturn(ok);

        AiProviderModelsDTO got = controller.addModel("p1", null, null);

        assertSame(ok, got);
        verify(aiProviderModelsAdminService).addProviderModel("p1", null, null, null);
    }

    @Test
    void addModel_shouldResolveUserId_whenPrincipalPresent() {
        AiProviderModelsAdminService aiProviderModelsAdminService = mock(AiProviderModelsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminAiProviderModelsController controller = new AdminAiProviderModelsController(aiProviderModelsAdminService, administratorService);

        UsersEntity u = new UsersEntity();
        u.setId(9L);
        when(administratorService.findByUsername("u")).thenReturn(Optional.of(u));

        AiProviderAddModelRequestDTO payload = new AiProviderAddModelRequestDTO();
        payload.setPurpose("chat");
        payload.setModelName("gpt-x");

        AiProviderModelsDTO ok = new AiProviderModelsDTO();
        when(aiProviderModelsAdminService.addProviderModel("p1", "chat", "gpt-x", 9L)).thenReturn(ok);

        Principal principal = () -> "u";
        AiProviderModelsDTO got = controller.addModel("p1", payload, principal);

        assertSame(ok, got);
        verify(aiProviderModelsAdminService).addProviderModel("p1", "chat", "gpt-x", 9L);
    }

    @Test
    void addModel_shouldUseNullUserId_whenUserNotFound() {
        AiProviderModelsAdminService aiProviderModelsAdminService = mock(AiProviderModelsAdminService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminAiProviderModelsController controller = new AdminAiProviderModelsController(aiProviderModelsAdminService, administratorService);

        when(administratorService.findByUsername("u")).thenReturn(Optional.empty());

        AiProviderAddModelRequestDTO payload = new AiProviderAddModelRequestDTO();
        payload.setPurpose("embed");
        payload.setModelName("m1");

        AiProviderModelsDTO ok = new AiProviderModelsDTO();
        when(aiProviderModelsAdminService.addProviderModel("p1", "embed", "m1", null)).thenReturn(ok);

        Principal principal = () -> "u";
        AiProviderModelsDTO got = controller.addModel("p1", payload, principal);

        assertSame(ok, got);
        verify(aiProviderModelsAdminService).addProviderModel("p1", "embed", "m1", null);
    }
}

