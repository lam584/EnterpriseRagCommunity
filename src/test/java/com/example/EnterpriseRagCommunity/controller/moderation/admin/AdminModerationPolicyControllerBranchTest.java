package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationPolicyConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationPolicyControllerBranchTest {

    @Mock
    private AdminModerationPolicyService service;

    @Test
    void getConfig_shouldDelegate() {
        AdminModerationPolicyController controller = new AdminModerationPolicyController(service);
        ModerationPolicyConfigDTO dto = new ModerationPolicyConfigDTO();
        when(service.getConfig(ContentType.POST)).thenReturn(dto);

        var out = controller.getConfig(ContentType.POST);

        assertSame(dto, out);
    }

    @Test
    void upsert_shouldCoverPrincipalNullAndNonNull() {
        AdminModerationPolicyController controller = new AdminModerationPolicyController(service);
        ModerationPolicyConfigDTO payload = new ModerationPolicyConfigDTO();
        ModerationPolicyConfigDTO dto = new ModerationPolicyConfigDTO();
        when(service.upsert(eq(payload), eq(null), eq(null))).thenReturn(dto);
        when(service.upsert(eq(payload), eq(null), eq("a@example.com"))).thenReturn(dto);

        var a = controller.upsert(payload, null);
        Principal principal = () -> "a@example.com";
        var b = controller.upsert(payload, principal);

        assertSame(dto, a);
        assertSame(dto, b);
        verify(service).upsert(payload, null, null);
        verify(service).upsert(payload, null, "a@example.com");
    }
}
