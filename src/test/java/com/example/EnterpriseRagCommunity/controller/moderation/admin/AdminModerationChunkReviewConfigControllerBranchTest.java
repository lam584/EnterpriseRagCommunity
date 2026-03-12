package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationChunkReviewConfigControllerBranchTest {

    @Mock
    private ModerationChunkReviewConfigService configService;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private AuditDiffBuilder auditDiffBuilder;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateConfig_writesAuditWithNullUsername_whenAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "N/A", List.of())
        );
        AdminModerationChunkReviewConfigController controller =
                new AdminModerationChunkReviewConfigController(configService, auditLogWriter, auditDiffBuilder);
        ModerationChunkReviewConfigDTO before = new ModerationChunkReviewConfigDTO();
        ModerationChunkReviewConfigDTO payload = new ModerationChunkReviewConfigDTO();
        ModerationChunkReviewConfigDTO after = new ModerationChunkReviewConfigDTO();
        when(configService.getConfig()).thenReturn(before);
        when(configService.updateConfig(payload)).thenReturn(after);
        when(auditDiffBuilder.build(before, after)).thenReturn(Map.of("changed", true));

        ModerationChunkReviewConfigDTO actual = controller.updateConfig(payload);

        assertSame(after, actual);
        verify(auditLogWriter).write(eq(null), eq(null), eq("CONFIG_CHANGE"), eq("MODERATION_CHUNK_REVIEW_CONFIG"),
                eq(null), any(), eq("更新分片审核配置"), eq(null), any());
    }

    @Test
    void updateConfig_writesAuditWithTrimmedUsername_whenAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  bob@example.com  ", "N/A", List.of())
        );
        AdminModerationChunkReviewConfigController controller =
                new AdminModerationChunkReviewConfigController(configService, auditLogWriter, auditDiffBuilder);
        ModerationChunkReviewConfigDTO before = new ModerationChunkReviewConfigDTO();
        ModerationChunkReviewConfigDTO payload = new ModerationChunkReviewConfigDTO();
        ModerationChunkReviewConfigDTO after = new ModerationChunkReviewConfigDTO();
        when(configService.getConfig()).thenReturn(before);
        when(configService.updateConfig(payload)).thenReturn(after);
        when(auditDiffBuilder.build(before, after)).thenReturn(Map.of());

        ModerationChunkReviewConfigDTO actual = controller.updateConfig(payload);

        assertSame(after, actual);
        verify(auditLogWriter).write(eq(null), eq("bob@example.com"), eq("CONFIG_CHANGE"), eq("MODERATION_CHUNK_REVIEW_CONFIG"),
                eq(null), any(), eq("更新分片审核配置"), eq(null), any());
    }

    @Test
    void getConfig_delegatesToService() {
        AdminModerationChunkReviewConfigController controller =
                new AdminModerationChunkReviewConfigController(configService, auditLogWriter, auditDiffBuilder);
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        when(configService.getConfig()).thenReturn(cfg);

        var out = controller.getConfig();

        assertSame(cfg, out);
    }

    @Test
    void currentUsernameOrNull_coversUnauthAndException() {
        SecurityContextHolder.clearContext();
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationChunkReviewConfigController.class, "currentUsernameOrNull"));

        Authentication unauth = org.mockito.Mockito.mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationChunkReviewConfigController.class, "currentUsernameOrNull"));

        Authentication broken = org.mockito.Mockito.mock(Authentication.class);
        when(broken.isAuthenticated()).thenReturn(true);
        when(broken.getPrincipal()).thenThrow(new RuntimeException("x"));
        SecurityContextHolder.getContext().setAuthentication(broken);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationChunkReviewConfigController.class, "currentUsernameOrNull"));

        Authentication namedNull = org.mockito.Mockito.mock(Authentication.class);
        when(namedNull.isAuthenticated()).thenReturn(true);
        when(namedNull.getPrincipal()).thenReturn("p");
        when(namedNull.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(namedNull);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationChunkReviewConfigController.class, "currentUsernameOrNull"));

        Authentication namedBlank = org.mockito.Mockito.mock(Authentication.class);
        when(namedBlank.isAuthenticated()).thenReturn(true);
        when(namedBlank.getPrincipal()).thenReturn("p");
        when(namedBlank.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(namedBlank);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminModerationChunkReviewConfigController.class, "currentUsernameOrNull"));
    }
}
