package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
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
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentsServiceImplCreateForPostTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static CommentsServiceImpl newService(
            CommentsRepository commentsRepository,
            AdministratorService administratorService,
            PostsRepository postsRepository,
            NotificationsService notificationsService,
            AdminModerationQueueService adminModerationQueueService,
            ModerationRuleAutoRunner moderationRuleAutoRunner,
            ModerationVecAutoRunner moderationVecAutoRunner,
            ModerationLlmAutoRunner moderationLlmAutoRunner,
            UsersRepository usersRepository,
            ReactionsRepository reactionsRepository,
            AiLanguageDetectService aiLanguageDetectService,
            AuditLogWriter auditLogWriter
    ) {
        CommentsServiceImpl svc = new CommentsServiceImpl();
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "notificationsService", notificationsService);
        ReflectionTestUtils.setField(svc, "adminModerationQueueService", adminModerationQueueService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", mock(ModerationQueueRepository.class));
        ReflectionTestUtils.setField(svc, "moderationAutoKickService", mock(ModerationAutoKickService.class));
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "reactionsRepository", reactionsRepository);
        ReflectionTestUtils.setField(svc, "aiLanguageDetectService", aiLanguageDetectService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        return svc;
    }

    private static CommentsServiceImpl newService(
            CommentsRepository commentsRepository,
            AdministratorService administratorService,
            PostsRepository postsRepository,
            NotificationsService notificationsService,
            AdminModerationQueueService adminModerationQueueService,
            ModerationQueueRepository moderationQueueRepository,
            ModerationAutoKickService moderationAutoKickService,
            UsersRepository usersRepository,
            ReactionsRepository reactionsRepository,
            AiLanguageDetectService aiLanguageDetectService,
            AuditLogWriter auditLogWriter
    ) {
        CommentsServiceImpl svc = new CommentsServiceImpl();
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "notificationsService", notificationsService);
        ReflectionTestUtils.setField(svc, "adminModerationQueueService", adminModerationQueueService);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "moderationAutoKickService", moderationAutoKickService);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "reactionsRepository", reactionsRepository);
        ReflectionTestUtils.setField(svc, "aiLanguageDetectService", aiLanguageDetectService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        return svc;
    }

    @Test
    void createForPost_when_postId_null_should_throw() {
        CommentsServiceImpl svc = newService(
                mock(CommentsRepository.class),
                mock(AdministratorService.class),
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setContent("x");
        assertThrows(IllegalArgumentException.class, () -> svc.createForPost(null, req));
    }

    @Test
    void createForPost_when_req_null_should_throw() {
        CommentsServiceImpl svc = newService(
                mock(CommentsRepository.class),
                mock(AdministratorService.class),
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        assertThrows(IllegalArgumentException.class, () -> svc.createForPost(1L, null));
    }

    @Test
    void createForPost_when_not_logged_in_should_audit_fail_and_throw() {
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        CommentsServiceImpl svc = newService(
                mock(CommentsRepository.class),
                mock(AdministratorService.class),
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setContent("hello");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.createForPost(1L, req));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("未登录"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(
                isNull(),
                isNull(),
                eq("COMMENT_CREATE"),
                eq("COMMENT"),
                isNull(),
                eq(AuditResult.FAIL),
                eq("未登录或会话已过期"),
                isNull(),
                detailsCaptor.capture()
        );
        assertEquals(1L, detailsCaptor.getValue().get("postId"));
        assertTrue(detailsCaptor.getValue().containsKey("parentId"));
        assertNull(detailsCaptor.getValue().get("parentId"));
    }

    @Test
    void createForPost_when_user_missing_should_audit_fail_and_throw() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsServiceImpl svc = newService(
                mock(CommentsRepository.class),
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setContent("hello");
        req.setParentId(9L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.createForPost(1L, req));
        assertEquals("当前用户不存在", ex.getMessage());

        verify(auditLogWriter).write(
                isNull(),
                eq("u@example.com"),
                eq("COMMENT_CREATE"),
                eq("COMMENT"),
                isNull(),
                eq(AuditResult.FAIL),
                eq("当前用户不存在"),
                isNull(),
                eq(Map.of("postId", 1L, "parentId", 9L))
        );
    }

    @Test
    void createForPost_parent_null_should_notify_audit_success_and_truncate_content_and_swallow_kick_exceptions() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        AiLanguageDetectService aiLanguageDetectService = mock(AiLanguageDetectService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(aiLanguageDetectService.detectLanguages(any())).thenReturn(List.of());
        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            CommentsEntity saved = new CommentsEntity();
            saved.setId(123L);
            saved.setPostId(e.getPostId());
            saved.setParentId(e.getParentId());
            saved.setAuthorId(e.getAuthorId());
            saved.setContent(e.getContent());
            saved.setStatus(null);
            saved.setMetadata(e.getMetadata());
            saved.setIsDeleted(false);
            saved.setCreatedAt(e.getCreatedAt());
            saved.setUpdatedAt(e.getUpdatedAt());
            return saved;
        });

        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(900L);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(any(), any(), eq(123L))).thenReturn(Optional.of(queue));
        doThrow(new IllegalStateException("boom")).when(moderationAutoKickService).kickQueueId(900L);

        PostsEntity post = new PostsEntity();
        post.setId(1L);
        post.setAuthorId(55L);
        post.setTitle("t");
        when(postsRepository.findById(eq(1L))).thenReturn(Optional.of(post));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                adminModerationQueueService,
            moderationQueueRepository,
            moderationAutoKickService,
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                aiLanguageDetectService,
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(null);
        req.setContent("a".repeat(600));

        CommentDTO out = svc.createForPost(1L, req);
        assertNotNull(out);
        assertEquals(123L, out.getId());
        assertNull(out.getStatus());

        verify(adminModerationQueueService).ensureEnqueuedComment(eq(123L));
    verify(moderationAutoKickService).kickQueueId(900L);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationsService).createNotification(eq(55L), eq("REPLY_POST"), eq("有人评论了你的帖子"), contentCaptor.capture());
        String notifyContent = contentCaptor.getValue();
        assertTrue(notifyContent.length() > 500);
        assertTrue(notifyContent.endsWith("..."));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> successDetailsCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(
                eq(7L),
                eq("u@example.com"),
                eq("COMMENT_CREATE"),
                eq("COMMENT"),
                eq(123L),
                eq(AuditResult.SUCCESS),
                eq("发表评论"),
                isNull(),
                successDetailsCaptor.capture()
        );
        assertEquals(1L, successDetailsCaptor.getValue().get("postId"));
        assertTrue(successDetailsCaptor.getValue().containsKey("parentId"));
        assertNull(successDetailsCaptor.getValue().get("parentId"));
        assertTrue(successDetailsCaptor.getValue().containsKey("status"));
        assertNull(successDetailsCaptor.getValue().get("status"));
    }

    @Test
    void createForPost_parent_not_null_should_not_notify() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(124L);
            return e;
        });

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                notificationsService,
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(9L);
        req.setContent("x");

        svc.createForPost(1L, req);

        verify(notificationsService, never()).createNotification(anyLong(), any(), any(), any());
        verify(auditLogWriter).write(
                eq(7L),
                eq("u@example.com"),
                eq("COMMENT_CREATE"),
                eq("COMMENT"),
                eq(124L),
                eq(AuditResult.SUCCESS),
                eq("发表评论"),
                isNull(),
                eq(Map.of("postId", 1L, "parentId", 9L, "status", "PENDING"))
        );
    }

    @Test
    void createForPost_when_save_throws_should_audit_fail_with_sanitized_message_and_rethrow() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        String raw = "\n\t" + "x".repeat(700) + "\n";
        when(commentsRepository.save(any())).thenThrow(new IllegalStateException(raw));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(null);
        req.setContent("x");

        assertThrows(IllegalStateException.class, () -> svc.createForPost(1L, req));

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> failDetailsCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(auditLogWriter).write(
                eq(7L),
                eq("u@example.com"),
                eq("COMMENT_CREATE"),
                eq("COMMENT"),
                isNull(),
                eq(AuditResult.FAIL),
                msgCaptor.capture(),
                isNull(),
                failDetailsCaptor.capture()
        );
        assertNotNull(msgCaptor.getValue());
        assertEquals(512, msgCaptor.getValue().length());
        assertEquals(1L, failDetailsCaptor.getValue().get("postId"));
        assertTrue(failDetailsCaptor.getValue().containsKey("parentId"));
        assertNull(failDetailsCaptor.getValue().get("parentId"));
    }

    @Test
    void createForPost_when_detect_returns_null_should_not_write_languages() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AiLanguageDetectService aiLanguageDetectService = mock(AiLanguageDetectService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(aiLanguageDetectService.detectLanguages(any())).thenReturn(null);
        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(200L);
            return e;
        });

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                aiLanguageDetectService,
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(9L);
        req.setContent("x");

        svc.createForPost(1L, req);

        ArgumentCaptor<CommentsEntity> entityCaptor = ArgumentCaptor.forClass(CommentsEntity.class);
        verify(commentsRepository).save(entityCaptor.capture());
        assertNull(entityCaptor.getValue().getMetadata());
    }

    @Test
    void createForPost_parent_null_when_post_null_should_not_notify() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(201L);
            return e;
        });
        when(postsRepository.findById(eq(1L))).thenReturn(Optional.empty());

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(null);
        req.setContent("x");

        svc.createForPost(1L, req);

        verify(notificationsService, never()).createNotification(anyLong(), any(), any(), any());
    }

    @Test
    void createForPost_parent_null_when_post_author_id_null_should_not_notify() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(202L);
            return e;
        });
        PostsEntity post = new PostsEntity();
        post.setId(1L);
        post.setAuthorId(null);
        post.setTitle("t");
        when(postsRepository.findById(eq(1L))).thenReturn(Optional.of(post));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(null);
        req.setContent("x");

        svc.createForPost(1L, req);

        verify(notificationsService, never()).createNotification(anyLong(), any(), any(), any());
    }

    @Test
    void createForPost_parent_null_when_post_author_is_me_should_not_notify() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(203L);
            return e;
        });
        PostsEntity post = new PostsEntity();
        post.setId(1L);
        post.setAuthorId(7L);
        post.setTitle("t");
        when(postsRepository.findById(eq(1L))).thenReturn(Optional.of(post));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(null);
        req.setContent("x");

        svc.createForPost(1L, req);

        verify(notificationsService, never()).createNotification(anyLong(), any(), any(), any());
    }

    @Test
    void createForPost_parent_null_title_null_should_notify_without_prefix_and_without_truncation() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(204L);
            return e;
        });
        PostsEntity post = new PostsEntity();
        post.setId(1L);
        post.setAuthorId(55L);
        post.setTitle(null);
        when(postsRepository.findById(eq(1L))).thenReturn(Optional.of(post));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(null);
        req.setContent("hi");

        svc.createForPost(1L, req);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationsService).createNotification(eq(55L), eq("REPLY_POST"), eq("有人评论了你的帖子"), contentCaptor.capture());
        assertEquals("收到了新的评论：hi", contentCaptor.getValue());
    }

    @Test
    void createForPost_shouldKickAfterCommit_whenTransactionSynchronizationActive() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(205L);
            return e;
        });
        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(901L);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(any(), any(), eq(205L))).thenReturn(Optional.of(queue));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                moderationQueueRepository,
                moderationAutoKickService,
                mock(UsersRepository.class),
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setParentId(9L);
        req.setContent("x");

        TransactionSynchronizationManager.initSynchronization();

        svc.createForPost(1L, req);

        verify(moderationAutoKickService, never()).kickQueueId(anyLong());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());

        syncs.get(0).afterCommit();

        verify(moderationAutoKickService).kickQueueId(901L);
    }
}
