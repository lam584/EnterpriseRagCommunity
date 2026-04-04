package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;


import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentsServiceImplDeleteOwnCommentTest {

    private static CommentsServiceImpl newService(
            CommentsRepository commentsRepository,
            AdministratorService administratorService,
            ModerationQueueRepository moderationQueueRepository,
            RagCommentIndexVisibilitySyncService ragCommentIndexVisibilitySyncService,
            AuditLogWriter auditLogWriter
    ) {
        return new CommentsServiceImpl(
                commentsRepository,
                administratorService,
                mock(com.example.EnterpriseRagCommunity.repository.content.PostsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.monitor.NotificationsService.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService.class),
                moderationQueueRepository,
                mock(com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.UsersRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.ai.AiLanguageDetectService.class),
                auditLogWriter,
                ragCommentIndexVisibilitySyncService
        );
    }


    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteOwnComment_whenNotLoggedIn_shouldAuditFailAndThrow() {
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        CommentsServiceImpl svc = newService(
                mock(CommentsRepository.class),
                mock(AdministratorService.class),
                mock(ModerationQueueRepository.class),
                mock(RagCommentIndexVisibilitySyncService.class),
                auditLogWriter
        );

        assertThrows(RuntimeException.class, () -> svc.deleteOwnComment(1L));

        verify(auditLogWriter).write(
                isNull(),
                isNull(),
                eq("COMMENT_DELETE"),
                eq("COMMENT"),
                eq(1L),
                eq(AuditResult.FAIL),
                eq("未登录或会话已过期"),
                isNull(),
                anyMap()
        );
    }

    @Test
    void deleteOwnComment_whenNotAuthor_shouldThrowAndAuditFail() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsEntity comment = new CommentsEntity();
        comment.setId(1L);
        comment.setAuthorId(8L);
        comment.setIsDeleted(false);
        when(commentsRepository.findById(1L)).thenReturn(Optional.of(comment));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(ModerationQueueRepository.class),
                mock(RagCommentIndexVisibilitySyncService.class),
                auditLogWriter
        );

        assertThrows(IllegalArgumentException.class, () -> svc.deleteOwnComment(1L));

        verify(commentsRepository, never()).save(any());
        verify(auditLogWriter).write(
                eq(7L),
                eq("u@example.com"),
                eq("COMMENT_DELETE"),
                eq("COMMENT"),
                eq(1L),
                eq(AuditResult.FAIL),
                eq("无权删除该评论"),
                isNull(),
                anyMap()
        );
    }

    @Test
    void deleteOwnComment_success_shouldSoftDeleteAndSync() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        RagCommentIndexVisibilitySyncService ragCommentIndexVisibilitySyncService = mock(RagCommentIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsEntity comment = new CommentsEntity();
        comment.setId(1L);
        comment.setPostId(9L);
        comment.setAuthorId(7L);
        comment.setStatus(CommentStatus.VISIBLE);
        comment.setIsDeleted(false);
        when(commentsRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentsRepository.save(any(CommentsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ModerationQueueEntity queue = new ModerationQueueEntity();
        queue.setId(99L);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(
                ModerationCaseType.CONTENT,
                ContentType.COMMENT,
                1L
        )).thenReturn(Optional.of(queue));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                moderationQueueRepository,
                ragCommentIndexVisibilitySyncService,
                auditLogWriter
        );

        svc.deleteOwnComment(1L);

        verify(commentsRepository).save(any(CommentsEntity.class));
        verify(moderationQueueRepository).delete(eq(queue));
        verify(ragCommentIndexVisibilitySyncService).scheduleSyncAfterCommit(1L);
        verify(auditLogWriter).write(
                eq(7L),
                eq("u@example.com"),
                eq("COMMENT_DELETE"),
                eq("COMMENT"),
                eq(1L),
                eq(AuditResult.SUCCESS),
                eq("删除评论"),
                isNull(),
                anyMap()
        );
    }

    @Test
    void deleteOwnComment_whenAlreadyDeleted_shouldReturnSilently() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsEntity comment = new CommentsEntity();
        comment.setId(1L);
        comment.setAuthorId(7L);
        comment.setIsDeleted(true);
        when(commentsRepository.findById(1L)).thenReturn(Optional.of(comment));

        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(ModerationQueueRepository.class),
                mock(RagCommentIndexVisibilitySyncService.class),
                auditLogWriter
        );

        svc.deleteOwnComment(1L);

        verify(commentsRepository, never()).save(any());
        verify(auditLogWriter, never()).write(any(), any(), any(), any(), any(), any(), any(), any(), anyMap());
    }
}
