package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.PostComposeConfigService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentAdminControllersBranchTest {

    @Mock
    private PostsService postsService;
    @Mock
    private PostComposeConfigService postComposeConfigService;
    @Mock
    private AuditLogWriter auditLogWriter;
    @Mock
    private AuditDiffBuilder auditDiffBuilder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminPostsList_shouldCoverStatusBranches() {
        AdminPostsController controller = new AdminPostsController(postsService);
        when(postsService.query(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.<PostsEntity>of(), PageRequest.of(0, 20), 0));

        controller.list(null, null, null, null, null, null, null, null, 1, 20, null, null);
        controller.list(null, null, null, null, "   ", null, null, null, 1, 20, null, null);
        controller.list(null, null, null, null, "ALL", null, null, null, 1, 20, null, null);
        controller.list(null, null, null, null, "PUBLISHED", null, null, null, 1, 20, null, null);

        ArgumentCaptor<PostStatus> statusCaptor = ArgumentCaptor.forClass(PostStatus.class);
        verify(postsService, org.mockito.Mockito.times(4))
                .query(any(), any(), any(), any(), statusCaptor.capture(), any(), any(), any(), anyInt(), anyInt(), any(), any());
        assertNull(statusCaptor.getAllValues().get(0));
        assertNull(statusCaptor.getAllValues().get(1));
        assertNull(statusCaptor.getAllValues().get(2));
        assertEquals(PostStatus.PUBLISHED, statusCaptor.getAllValues().get(3));
    }

    @Test
    void postComposeConfigController_shouldCoverCurrentUsernameBranches() {
        AdminPostComposeConfigController controller =
                new AdminPostComposeConfigController(postComposeConfigService, auditLogWriter, auditDiffBuilder);

        PostComposeConfigDTO before = new PostComposeConfigDTO();
        before.setRequireTitle(false);
        PostComposeConfigDTO after = new PostComposeConfigDTO();
        after.setRequireTitle(true);
        when(postComposeConfigService.getConfig()).thenReturn(before);
        when(postComposeConfigService.updateConfig(any())).thenReturn(after);
        when(auditDiffBuilder.build(before, after)).thenReturn(Map.of("changed", true));

        PostComposeConfigDTO payload = new PostComposeConfigDTO();
        PostComposeConfigDTO updated = controller.updateConfig(payload);
        assertSame(after, updated);
        assertSame(before, controller.getConfig());
        verify(auditLogWriter).write(
                isNull(),
                isNull(),
                eq("ADMIN_SETTINGS_UPDATE"),
                eq("POST_COMPOSE_CONFIG"),
                isNull(),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("更新发帖表单配置"),
                isNull(),
                any()
        );

        SecurityContextHolder.clearContext();
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));

        Authentication unauthenticated = mock(Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", "n/a", List.of())
        );
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));

        Authentication nullName = mock(Authentication.class);
        when(nullName.isAuthenticated()).thenReturn(true);
        when(nullName.getPrincipal()).thenReturn("user");
        when(nullName.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(nullName);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));

        Authentication blankName = mock(Authentication.class);
        when(blankName.isAuthenticated()).thenReturn(true);
        when(blankName.getPrincipal()).thenReturn("user");
        when(blankName.getName()).thenReturn("   ");
        SecurityContextHolder.getContext().setAuthentication(blankName);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("  alice@example.com  ", "n/a", List.of())
        );
        assertEquals("alice@example.com",
                ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));

        Authentication broken = mock(Authentication.class);
        when(broken.isAuthenticated()).thenReturn(true);
        when(broken.getPrincipal()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(broken);
        assertNull((String) ReflectionTestUtils.invokeMethod(AdminPostComposeConfigController.class, "currentUsernameOrNull"));
    }
}
