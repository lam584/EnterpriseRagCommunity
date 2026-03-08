package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostsServiceImplStatusDeleteTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateStatusValidatesIdAndStatus() {
        PostsServiceImpl svc = new PostsServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(null, PostStatus.PUBLISHED));
        assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(1L, null));
    }

    @Test
    void updateStatusSetsPublishedAtWhenPublishing() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        RagPostIndexVisibilitySyncService sync = mock(RagPostIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity post = new PostsEntity();
        post.setId(100L);
        post.setIsDeleted(false);
        post.setStatus(PostStatus.PENDING);
        post.setPublishedAt(null);
        when(postsRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(sync).scheduleSyncAfterCommit(anyLong());
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsServiceImpl svc = new PostsServiceImpl();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "ragPostIndexVisibilitySyncService", sync);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);

        PostsEntity saved = svc.updateStatus(100L, PostStatus.PUBLISHED);
        assertNotNull(saved.getPublishedAt());
        assertEquals(PostStatus.PUBLISHED, saved.getStatus());
        verify(sync).scheduleSyncAfterCommit(100L);
    }

    @Test
    void updateStatusDoesNotOverridePublishedAtWhenAlreadySet() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        RagPostIndexVisibilitySyncService sync = mock(RagPostIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        LocalDateTime publishedAt = LocalDateTime.of(2020, 1, 1, 0, 0);
        PostsEntity post = new PostsEntity();
        post.setId(100L);
        post.setIsDeleted(false);
        post.setStatus(PostStatus.PENDING);
        post.setPublishedAt(publishedAt);
        when(postsRepository.findById(100L)).thenReturn(Optional.of(post));
        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(sync).scheduleSyncAfterCommit(anyLong());
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsServiceImpl svc = new PostsServiceImpl();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "ragPostIndexVisibilitySyncService", sync);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);

        PostsEntity saved = svc.updateStatus(100L, PostStatus.PUBLISHED);
        assertSame(publishedAt, saved.getPublishedAt());
    }

    @Test
    void updateStatusThrowsWhenDeletedAndWritesAuditOnAuthMissing() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        RagPostIndexVisibilitySyncService sync = mock(RagPostIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity post = new PostsEntity();
        post.setId(100L);
        post.setIsDeleted(true);
        when(postsRepository.findById(100L)).thenReturn(Optional.of(post));
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsServiceImpl svc = new PostsServiceImpl();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "ragPostIndexVisibilitySyncService", sync);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(100L, PostStatus.PUBLISHED));
        assertEquals("帖子已删除: 100", ex.getMessage());

        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationException.class, () -> svc.updateStatus(100L, PostStatus.PUBLISHED));
        verify(auditLogWriter).write(any(), any(), eq("POST_UPDATE_STATUS"), eq("POST"), eq(100L), any(), any(), any(), any());
    }

    @Test
    void getByIdValidatesAndFinds() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PostsServiceImpl svc = new PostsServiceImpl();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);

        assertThrows(IllegalArgumentException.class, () -> svc.getById(null));

        when(postsRepository.findByIdAndIsDeletedFalse(1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> svc.getById(1L));
    }

    @Test
    void deleteReturnsWhenAlreadyDeletedAndDeletesModerationQueueWhenPresent() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        RagPostIndexVisibilitySyncService sync = mock(RagPostIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsEntity alreadyDeleted = new PostsEntity();
        alreadyDeleted.setId(1L);
        alreadyDeleted.setIsDeleted(true);
        when(postsRepository.findById(1L)).thenReturn(Optional.of(alreadyDeleted));

        PostsServiceImpl svc = new PostsServiceImpl();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "postAttachmentsRepository", postAttachmentsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "ragPostIndexVisibilitySyncService", sync);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);

        svc.delete(1L);
        verify(postsRepository, never()).save(any());
        verify(postAttachmentsRepository, never()).deleteByPostId(anyLong());

        PostsEntity post = new PostsEntity();
        post.setId(2L);
        post.setIsDeleted(false);
        post.setAuthorId(10L);
        when(postsRepository.findById(2L)).thenReturn(Optional.of(post));
        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(9L);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, 2L))
                .thenReturn(Optional.of(q));

        svc.delete(2L);
        verify(postAttachmentsRepository).deleteByPostId(2L);
        verify(moderationQueueRepository).delete(q);
        verify(sync).scheduleSyncAfterCommit(2L);
    }

    @Test
    void deleteThrowsWhenNotAuthorAndWhenIdNull() {
        PostsServiceImpl svc0 = new PostsServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> svc0.delete(null));

        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        RagPostIndexVisibilitySyncService sync = mock(RagPostIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        doNothing().when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsEntity post = new PostsEntity();
        post.setId(3L);
        post.setIsDeleted(false);
        post.setAuthorId(11L);
        when(postsRepository.findById(3L)).thenReturn(Optional.of(post));

        PostsServiceImpl svc = new PostsServiceImpl();
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "postAttachmentsRepository", postAttachmentsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "ragPostIndexVisibilitySyncService", sync);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.delete(3L));
        assertEquals("无权删除该帖子", ex.getMessage());
        verify(auditLogWriter).write(eq(10L), any(), eq("POST_DELETE"), eq("POST"), eq(3L), any(), any(), any(), any());
    }
}
