package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationConfidenceFallbackConfigDTO;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationFallbackService;
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
class AdminModerationFallbackControllerBranchTest {

    @Mock
    private AdminModerationFallbackService service;

    @Test
    void upsert_usesNullUsername_whenPrincipalIsNull() {
        AdminModerationFallbackController controller = new AdminModerationFallbackController(service);
        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        ModerationConfidenceFallbackConfigDTO saved = new ModerationConfidenceFallbackConfigDTO();
        when(service.upsert(eq(payload), eq(null), eq(null))).thenReturn(saved);

        ModerationConfidenceFallbackConfigDTO actual = controller.upsert(payload, null);

        assertSame(saved, actual);
        verify(service).upsert(payload, null, null);
    }

    @Test
    void upsert_usesPrincipalName_whenPrincipalProvided() {
        AdminModerationFallbackController controller = new AdminModerationFallbackController(service);
        ModerationConfidenceFallbackConfigDTO payload = new ModerationConfidenceFallbackConfigDTO();
        ModerationConfidenceFallbackConfigDTO saved = new ModerationConfidenceFallbackConfigDTO();
        Principal principal = () -> "alice@example.com";
        when(service.upsert(eq(payload), eq(null), eq("alice@example.com"))).thenReturn(saved);

        ModerationConfidenceFallbackConfigDTO actual = controller.upsert(payload, principal);

        assertSame(saved, actual);
        verify(service).upsert(payload, null, "alice@example.com");
    }
}
