package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.AiLanguageDetectService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentsServiceImplListByPostIdTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
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
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "reactionsRepository", reactionsRepository);
        ReflectionTestUtils.setField(svc, "aiLanguageDetectService", aiLanguageDetectService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        return svc;
    }

    private static CommentsEntity newComment(Long id, Long postId, Long parentId, Long authorId, String content, CommentStatus status) {
        CommentsEntity e = new CommentsEntity();
        e.setId(id);
        e.setPostId(postId);
        e.setParentId(parentId);
        e.setAuthorId(authorId);
        e.setContent(content);
        e.setStatus(status);
        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    @Test
    void listByPostId_when_postId_null_should_throw() {
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

        assertThrows(IllegalArgumentException.class, () -> svc.listByPostId(null, 1, 20, false));
    }

    @Test
    void listByPostId_includeMinePending_loggedIn_routes_and_populates_like_and_author() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ReactionsRepository reactionsRepository = mock(ReactionsRepository.class);
        AiLanguageDetectService aiLanguageDetectService = mock(AiLanguageDetectService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsEntity c1 = newComment(1L, 99L, null, 11L, "c1", CommentStatus.VISIBLE);
        CommentsEntity c2 = newComment(2L, 99L, null, 22L, "c2", CommentStatus.VISIBLE);
        CommentsEntity c3 = newComment(null, 99L, null, null, "c3", CommentStatus.VISIBLE);
        Page<CommentsEntity> page = new PageImpl<>(List.of(c1, c2, c3));
        when(commentsRepository.findVisibleOrMinePending(eq(99L), eq(7L), any(Pageable.class))).thenReturn(page);

        UsersEntity u11 = new UsersEntity();
        u11.setId(11L);
        u11.setUsername("alice");
        Map<String, Object> profile = new java.util.HashMap<>();
        profile.put("avatarUrl", 123);
        profile.put("location", null);
        u11.setMetadata(Map.of("profile", profile));
        when(usersRepository.findAllById(eq(Set.of(11L, 22L)))).thenReturn(List.of(u11));

        java.util.ArrayList<Object[]> groupedRows = new java.util.ArrayList<>();
        groupedRows.add(null);
        groupedRows.add(new Object[]{1L});
        groupedRows.add(new Object[]{null, 5L});
        groupedRows.add(new Object[]{1L, null});
        groupedRows.add(new Object[]{2L, 7L});
        when(reactionsRepository.countByTargetIdsGrouped(eq(ReactionTargetType.COMMENT), eq(ReactionType.LIKE), eq(List.of(1L, 2L))))
                .thenReturn(groupedRows);
        when(reactionsRepository.findTargetIdsLikedByUser(eq(7L), eq(ReactionTargetType.COMMENT), eq(ReactionType.LIKE), eq(List.of(1L, 2L))))
                .thenReturn(List.of(2L));

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                postsRepository,
                notificationsService,
                adminModerationQueueService,
                moderationRuleAutoRunner,
                moderationVecAutoRunner,
                moderationLlmAutoRunner,
                usersRepository,
                reactionsRepository,
                aiLanguageDetectService,
                auditLogWriter
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        List<CommentDTO> out = svc.listByPostId(99L, 0, 500, true).getContent();

        verify(commentsRepository).findVisibleOrMinePending(eq(99L), eq(7L), pageableCaptor.capture());
        verify(commentsRepository, never()).findByPostIdAndStatusAndIsDeletedFalse(any(), any(), any());

        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(200, pageable.getPageSize());
        assertNotNull(pageable.getSort().getOrderFor("createdAt"));
        assertEquals(org.springframework.data.domain.Sort.Direction.DESC, pageable.getSort().getOrderFor("createdAt").getDirection());

        assertEquals(3, out.size());

        CommentDTO dto1 = out.get(0);
        assertEquals(1L, dto1.getId());
        assertEquals(0L, dto1.getLikeCount());
        assertEquals(false, dto1.getLikedByMe());
        assertEquals("alice", dto1.getAuthorName());
        assertEquals("123", dto1.getAuthorAvatarUrl());
        assertNull(dto1.getAuthorLocation());

        CommentDTO dto2 = out.get(1);
        assertEquals(2L, dto2.getId());
        assertEquals(7L, dto2.getLikeCount());
        assertEquals(true, dto2.getLikedByMe());
        assertNull(dto2.getAuthorName());
        assertNull(dto2.getAuthorAvatarUrl());
        assertNull(dto2.getAuthorLocation());

        CommentDTO dto3 = out.get(2);
        assertNull(dto3.getId());
        assertEquals(0L, dto3.getLikeCount());
        assertEquals(false, dto3.getLikedByMe());
    }

    @Test
    void listByPostId_includeMinePending_anonymous_routes_to_visible_only_and_handles_empty() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ReactionsRepository reactionsRepository = mock(ReactionsRepository.class);

        when(commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(eq(9L), eq(CommentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(usersRepository.findAllById(eq(Set.of()))).thenReturn(List.of());

        CommentsServiceImpl svc = newService(
                commentsRepository,
                mock(AdministratorService.class),
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                usersRepository,
                reactionsRepository,
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        List<CommentDTO> out = svc.listByPostId(9L, 1, 20, true).getContent();

        assertTrue(out.isEmpty());
        verify(commentsRepository).findByPostIdAndStatusAndIsDeletedFalse(eq(9L), eq(CommentStatus.VISIBLE), any(Pageable.class));
        verify(commentsRepository, never()).findVisibleOrMinePending(any(), any(), any());
        verify(reactionsRepository, never()).countByTargetIdsGrouped(any(), any(), any());
        verify(reactionsRepository, never()).findTargetIdsLikedByUser(any(), any(), any(), any());
    }

    @Test
    void listByPostId_includeMinePending_false_should_not_request_mine_pending_page() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        CommentsEntity c1 = newComment(1L, 11L, null, 11L, "c1", CommentStatus.VISIBLE);
        when(commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(eq(11L), eq(CommentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1)));
        when(usersRepository.findAllById(eq(Set.of(11L)))).thenReturn(List.of());

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                usersRepository,
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        svc.listByPostId(11L, 1, 20, false);

        verify(commentsRepository).findByPostIdAndStatusAndIsDeletedFalse(eq(11L), eq(CommentStatus.VISIBLE), any(Pageable.class));
        verify(commentsRepository, never()).findVisibleOrMinePending(any(), any(), any());
    }

    @Test
    void listByPostId_when_pageSize_non_positive_should_default_to_20() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);

        when(commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(eq(9L), eq(CommentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(usersRepository.findAllById(eq(Set.of()))).thenReturn(List.of());

        CommentsServiceImpl svc = newService(
                commentsRepository,
                mock(AdministratorService.class),
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                usersRepository,
                mock(ReactionsRepository.class),
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        svc.listByPostId(9L, 1, 0, false);
        verify(commentsRepository).findByPostIdAndStatusAndIsDeletedFalse(eq(9L), eq(CommentStatus.VISIBLE), pageableCaptor.capture());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void listByPostId_logged_in_but_no_comments_should_not_query_liked_ids() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ReactionsRepository reactionsRepository = mock(ReactionsRepository.class);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(me));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u@example.com", "n/a", List.of()));

        when(commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(eq(9L), eq(CommentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(usersRepository.findAllById(eq(Set.of()))).thenReturn(List.of());

        CommentsServiceImpl svc = newService(
                commentsRepository,
                administratorService,
                mock(PostsRepository.class),
                mock(NotificationsService.class),
                mock(AdminModerationQueueService.class),
                mock(ModerationRuleAutoRunner.class),
                mock(ModerationVecAutoRunner.class),
                mock(ModerationLlmAutoRunner.class),
                usersRepository,
                reactionsRepository,
                mock(AiLanguageDetectService.class),
                mock(AuditLogWriter.class)
        );

        svc.listByPostId(9L, 1, 20, false);

        verify(reactionsRepository, never()).findTargetIdsLikedByUser(any(), any(), any(), any());
    }
}
