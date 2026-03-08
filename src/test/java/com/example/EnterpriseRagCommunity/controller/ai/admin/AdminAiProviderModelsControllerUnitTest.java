package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.AiProviderModelsDTO;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiProviderModelsAdminService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAiProviderModelsControllerUnitTest {

    @Test
    void addModel_shouldAcceptNullPrincipal_andNullPayload() {
        AiProviderModelsAdminService svc = mock(AiProviderModelsAdminService.class);
        AdministratorService adminSvc = mock(AdministratorService.class);
        AdminAiProviderModelsController c = new AdminAiProviderModelsController(svc, adminSvc);

        AiProviderModelsDTO dto = new AiProviderModelsDTO();
        dto.setProviderId("p1");
        when(svc.addProviderModel(eq("p1"), eq(null), eq(null), eq(null))).thenReturn(dto);

        AiProviderModelsDTO out = c.addModel("p1", null, null);
        org.junit.jupiter.api.Assertions.assertEquals("p1", out.getProviderId());
        verify(svc).addProviderModel(eq("p1"), eq(null), eq(null), eq(null));
    }
}

