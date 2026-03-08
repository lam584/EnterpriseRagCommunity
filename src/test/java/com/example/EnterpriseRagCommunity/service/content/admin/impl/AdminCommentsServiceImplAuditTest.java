package com.example.EnterpriseRagCommunity.service.content.admin.impl;

import com.example.EnterpriseRagCommunity.dto.content.admin.CommentSetDeletedRequest;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentUpdateStatusRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminCommentsServiceImplAuditTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateStatusWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        RagCommentIndexVisibilitySyncService syncService = mock(RagCommentIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity actor = new UsersEntity();
        actor.setId(1L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(actor));

        CommentsEntity e = new CommentsEntity();
        e.setId(200L);
        e.setPostId(300L);
        e.setAuthorId(400L);
        e.setStatus(CommentStatus.VISIBLE);
        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        when(commentsRepository.findById(200L)).thenReturn(Optional.of(e));
        when(commentsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminCommentsServiceImpl svc = new AdminCommentsServiceImpl();
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "ragCommentIndexVisibilitySyncService", syncService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);

        CommentUpdateStatusRequest req = new CommentUpdateStatusRequest();
        req.setStatus("HIDDEN");

        svc.updateStatus(200L, req);

        verify(auditLogWriter).write(
                eq(1L),
                eq("alice@example.com"),
                eq("COMMENT_STATUS_UPDATE"),
                eq("COMMENT"),
                eq(200L),
                any(),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void setDeletedWritesAuditLog() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        RagCommentIndexVisibilitySyncService syncService = mock(RagCommentIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        UsersEntity actor = new UsersEntity();
        actor.setId(1L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(actor));

        CommentsEntity e = new CommentsEntity();
        e.setId(201L);
        e.setPostId(301L);
        e.setAuthorId(401L);
        e.setStatus(CommentStatus.VISIBLE);
        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        when(commentsRepository.findById(201L)).thenReturn(Optional.of(e));
        when(commentsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(java.util.Map.of());

        AdminCommentsServiceImpl svc = new AdminCommentsServiceImpl();
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "ragCommentIndexVisibilitySyncService", syncService);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);

        CommentSetDeletedRequest req = new CommentSetDeletedRequest();
        req.setIsDeleted(true);

        svc.setDeleted(201L, req);

        verify(auditLogWriter).write(
                eq(1L),
                eq("alice@example.com"),
                eq("COMMENT_DELETE_TOGGLE"),
                eq("COMMENT"),
                eq(201L),
                any(),
                any(),
                eq(null),
                any()
        );
    }
}
