package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.AiLanguageDetectService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentsServiceImplHelpersTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication auth(boolean authenticated, Object principal, String name) {
        Authentication a = mock(Authentication.class);
        when(a.isAuthenticated()).thenReturn(authenticated);
        when(a.getPrincipal()).thenReturn(principal);
        when(a.getName()).thenReturn(name);
        return a;
    }

    private static Method privateStaticMethod(String name, Class<?>... parameterTypes) throws Exception {
        Method m = CommentsServiceImpl.class.getDeclaredMethod(name, parameterTypes);
        m.setAccessible(true);
        return m;
    }

    private static CommentsServiceImpl newService(CommentsRepository commentsRepository, AdministratorService administratorService) {
        return new CommentsServiceImpl(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationQueueRepository.class),
                mock(ModerationAutoKickService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class),
                mock(RagCommentIndexVisibilitySyncService.class)
        );
    }

    @Test
    void currentUserIdOrThrow_should_throw_for_null_auth_not_authenticated_anonymous() {
        AdministratorService administratorService = mock(AdministratorService.class);
        CommentsServiceImpl svc = newService(mock(CommentsRepository.class), administratorService);

        SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        SecurityContextHolder.getContext().setAuthentication(auth(false, "p", "u@example.com"));
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "anonymousUser", "u@example.com"));
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));
    }

    @Test
    void currentUserIdOrThrow_should_throw_when_user_missing_and_return_when_ok() {
        AdministratorService administratorService = mock(AdministratorService.class);
        CommentsServiceImpl svc = newService(mock(CommentsRepository.class), administratorService);

        SecurityContextHolder.getContext().setAuthentication(auth(true, "p", "u@example.com"));
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow"));

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(u));
        Long out = ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrThrow");
        assertEquals(7L, out);
    }

    @Test
    void currentUserIdOrNull_should_return_null_for_anonymous_and_on_exception_and_when_user_missing() {
        AdministratorService administratorService = mock(AdministratorService.class);
        CommentsServiceImpl svc = newService(mock(CommentsRepository.class), administratorService);

        SecurityContextHolder.clearContext();
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "anonymousUser", "u@example.com"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));

        SecurityContextHolder.getContext().setAuthentication(auth(false, "p", "u@example.com"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));

        Authentication a = auth(true, "p", "u@example.com");
        when(a.getName()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(a);
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "p", "u@example.com"));
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.empty());
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));
    }

    @Test
    void currentUsernameOrNull_should_handle_null_auth_anonymous_blank_trim_and_exception() throws Exception {
        Method m = privateStaticMethod("currentUsernameOrNull");

        SecurityContextHolder.clearContext();
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "anonymousUser", "anonymousUser"));
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(auth(false, "p", "u@example.com"));
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "p", null));
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "p", "   "));
        assertNull(m.invoke(null));

        SecurityContextHolder.getContext().setAuthentication(auth(true, "p", "  u@example.com "));
        assertEquals("u@example.com", m.invoke(null));

        Authentication a = auth(true, "p", "u@example.com");
        when(a.getName()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(a);
        assertNull(m.invoke(null));
    }

    @Test
    void safeText_should_cover_all_branches() throws Exception {
        Method m = privateStaticMethod("safeText", String.class, int.class);

        assertNull(m.invoke(null, null, 10));
        assertNull(m.invoke(null, " \n\t  ", 10));
        assertEquals("", m.invoke(null, "x", 0));
        assertEquals("a b", m.invoke(null, "a\nb", 10));
        assertEquals("x".repeat(5), m.invoke(null, "x".repeat(10), 5));
    }

    @Test
    void extractProfileString_should_cover_nulls_and_types() throws Exception {
        Method m = privateStaticMethod("extractProfileString", UsersEntity.class, String.class);

        assertNull(m.invoke(null, null, "avatarUrl"));

        UsersEntity u = new UsersEntity();
        assertNull(m.invoke(null, u, "avatarUrl"));

        u.setMetadata(Map.of("profile", "not-a-map"));
        assertNull(m.invoke(null, u, "avatarUrl"));

        Map<String, Object> profileWithNull = new HashMap<>();
        profileWithNull.put("avatarUrl", null);
        u.setMetadata(Map.of("profile", profileWithNull));
        assertNull(m.invoke(null, u, "avatarUrl"));

        u.setMetadata(Map.of("profile", Map.of("avatarUrl", 123)));
        assertEquals("123", m.invoke(null, u, "avatarUrl"));
    }

    @Test
    void toDTO_should_cover_status_null_and_not_null() throws Exception {
        Method m = privateStaticMethod("toDTO", CommentsEntity.class);

        CommentsEntity e1 = new CommentsEntity();
        e1.setId(1L);
        e1.setPostId(2L);
        e1.setAuthorId(3L);
        e1.setContent("c");
        e1.setStatus(null);
        CommentDTO dto1 = (CommentDTO) m.invoke(null, e1);
        assertNull(dto1.getStatus());

        CommentsEntity e2 = new CommentsEntity();
        e2.setId(1L);
        e2.setPostId(2L);
        e2.setAuthorId(3L);
        e2.setContent("c");
        e2.setStatus(CommentStatus.PENDING);
        CommentDTO dto2 = (CommentDTO) m.invoke(null, e2);
        assertEquals("PENDING", dto2.getStatus());
    }

    @Test
    void countByPostId_should_cover_null_and_non_null() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        when(commentsRepository.countByPostIdAndStatusAndIsDeletedFalse(eq(9L), eq(CommentStatus.VISIBLE))).thenReturn(5L);

        CommentsServiceImpl svc = newService(commentsRepository, mock(AdministratorService.class));

        assertEquals(0L, svc.countByPostId(null));
        assertEquals(5L, svc.countByPostId(9L));
    }
}
