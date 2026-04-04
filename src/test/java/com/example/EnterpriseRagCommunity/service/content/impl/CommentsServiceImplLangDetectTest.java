package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiLanguageDetectService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentsServiceImplLangDetectTest {

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
        return new CommentsServiceImpl(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                adminModerationQueueService,
                moderationQueueRepository,
                moderationAutoKickService,
                mock(ModerationRuleAutoRunner.class),
                usersRepository,
                reactionsRepository,
                aiLanguageDetectService,
                auditLogWriter,
                mock(RagCommentIndexVisibilitySyncService.class)
        );
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createForPost_writes_metadata_languages_when_detect_ok() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ReactionsRepository reactionsRepository = mock(ReactionsRepository.class);
        AiLanguageDetectService aiLanguageDetectService = mock(AiLanguageDetectService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        when(aiLanguageDetectService.detectLanguages(any())).thenReturn(List.of("en-us"));
        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(123L);
            return e;
        });

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                adminModerationQueueService,
                moderationQueueRepository,
                moderationAutoKickService,
                usersRepository,
                reactionsRepository,
                aiLanguageDetectService,
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setContent("hello");
        req.setParentId(99L);

        CommentDTO out = svc.createForPost(1L, req);
        assertNotNull(out);
        assertEquals(123L, out.getId());
        assertNotNull(out.getMetadata());
        assertEquals(List.of("en-us"), out.getMetadata().get("languages"));

        ArgumentCaptor<CommentsEntity> captor = ArgumentCaptor.forClass(CommentsEntity.class);
        verify(commentsRepository).save(captor.capture());
        Map<String, Object> meta = captor.getValue().getMetadata();
        assertNotNull(meta);
        assertEquals(List.of("en-us"), meta.get("languages"));
    }

    @Test
    void createForPost_when_detect_fails_should_not_block_comment() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationAutoKickService moderationAutoKickService = mock(ModerationAutoKickService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ReactionsRepository reactionsRepository = mock(ReactionsRepository.class);
        AiLanguageDetectService aiLanguageDetectService = mock(AiLanguageDetectService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        when(aiLanguageDetectService.detectLanguages(any())).thenThrow(new IllegalStateException("boom"));
        when(commentsRepository.save(any())).thenAnswer(inv -> {
            CommentsEntity e = inv.getArgument(0);
            e.setId(124L);
            return e;
        });

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                adminModerationQueueService,
                moderationQueueRepository,
                moderationAutoKickService,
                usersRepository,
                reactionsRepository,
                aiLanguageDetectService,
                auditLogWriter
        );

        CommentCreateRequest req = new CommentCreateRequest();
        req.setContent("hello");
        req.setParentId(99L);

        CommentDTO out = svc.createForPost(1L, req);
        assertNotNull(out);
        assertEquals(124L, out.getId());

        ArgumentCaptor<CommentsEntity> captor = ArgumentCaptor.forClass(CommentsEntity.class);
        verify(commentsRepository).save(captor.capture());
        assertNull(captor.getValue().getMetadata());
    }
}
