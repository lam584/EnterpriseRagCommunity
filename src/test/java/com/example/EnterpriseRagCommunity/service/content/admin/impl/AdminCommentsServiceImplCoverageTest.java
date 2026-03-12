package com.example.EnterpriseRagCommunity.service.content.admin.impl;

import com.example.EnterpriseRagCommunity.dto.content.admin.CommentSetDeletedRequest;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentUpdateStatusRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCommentsServiceImplCoverageTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void private_helpers_should_cover_branches() {
        assertNull(ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "buildPostExcerpt", null, 10));
        assertEquals("", ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "buildPostExcerpt", "   \n\t", 10));
        assertEquals("abc", ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "buildPostExcerpt", "abc", 0));
        assertEquals("abc", ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "buildPostExcerpt", "abc", 10));
        assertEquals("ab…", ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "buildPostExcerpt", "abc", 2));
    }

    @Test
    void list_should_cover_author_filter_quick_return_and_mapping() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        AdminCommentsServiceImpl svc = buildService(commentsRepository, usersRepository, postsRepository, administratorService);

        when(usersRepository.findAll(any(Specification.class))).thenReturn(List.of());
        Page<?> empty = svc.list(1, 20, null, null, "nobody", null, null, null, null, null);
        assertTrue(empty.isEmpty());

        UsersEntity user = new UsersEntity();
        user.setId(99L);
        when(usersRepository.findAll(any(Specification.class))).thenReturn(List.of(user));

        UsersEntity author = new UsersEntity();
        author.setUsername("authorName");
        when(administratorService.findById(99L)).thenReturn(Optional.of(author));

        PostsEntity post = new PostsEntity();
        post.setId(10L);
        post.setTitle("标题");
        post.setContent("正文内容");
        when(postsRepository.findAllById(anyCollection())).thenReturn(List.of(post));

        CommentsEntity c = new CommentsEntity();
        c.setId(1L);
        c.setPostId(10L);
        c.setAuthorId(99L);
        c.setStatus(CommentStatus.VISIBLE);
        c.setIsDeleted(null);
        c.setContent("内容");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());

        when(commentsRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Specification<CommentsEntity> spec = inv.getArgument(0);
                    invokeSpecification(spec);
                    return new PageImpl<>(List.of(c));
                });

        Page<?> page = svc.list(0, 999, 10L, 99L, "author", LocalDateTime.now().minusDays(1), LocalDateTime.now(), "VISIBLE", false, "123");
        assertEquals(1, page.getTotalElements());
        Object dto = page.getContent().get(0);
        assertNotNull(ReflectionTestUtils.getField(dto, "authorName"));
        assertNotNull(ReflectionTestUtils.getField(dto, "postExcerpt"));
    }

    @Test
    void list_should_cover_invalid_status_and_keyword_non_numeric_and_deleted_true() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);

        AdminCommentsServiceImpl svc = buildService(commentsRepository, usersRepository, postsRepository, administratorService);

        CommentsEntity c = new CommentsEntity();
        c.setId(2L);
        c.setPostId(null);
        c.setAuthorId(100L);
        c.setStatus(CommentStatus.HIDDEN);
        c.setIsDeleted(true);
        c.setContent("内容");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());

        UsersEntity author = new UsersEntity();
        author.setUsername(" ");
        author.setEmail("email@example.com");
        when(administratorService.findById(100L)).thenReturn(Optional.of(author));

        when(commentsRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Specification<CommentsEntity> spec = inv.getArgument(0);
                    invokeSpecification(spec);
                    return new PageImpl<>(List.of(c));
                });

        Page<?> page = svc.list(1, -1, null, null, null, null, null, "bad-status", true, "abc");
        assertEquals(1, page.getTotalElements());
        Object dto = page.getContent().get(0);
        assertEquals("email@example.com", ReflectionTestUtils.getField(dto, "authorName"));
        assertNull(ReflectionTestUtils.getField(dto, "postTitle"));
    }

    @Test
    void update_status_and_set_deleted_should_cover_validation_and_success_paths() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        RagCommentIndexVisibilitySyncService sync = mock(RagCommentIndexVisibilitySyncService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);

        AdminCommentsServiceImpl svc = buildService(commentsRepository, usersRepository, postsRepository, administratorService);
        ReflectionTestUtils.setField(svc, "ragCommentIndexVisibilitySyncService", sync);
        ReflectionTestUtils.setField(svc, "auditLogWriter", auditLogWriter);
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", auditDiffBuilder);

        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        CommentUpdateStatusRequest bad = new CommentUpdateStatusRequest();
        bad.setStatus("not-exists");
        assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(null, bad));
        assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(1L, null));
        assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(1L, bad));
        when(commentsRepository.findById(1L)).thenReturn(Optional.empty());
        CommentUpdateStatusRequest okReq = new CommentUpdateStatusRequest();
        okReq.setStatus("visible");
        assertThrows(IllegalArgumentException.class, () -> svc.updateStatus(1L, okReq));

        CommentsEntity e = new CommentsEntity();
        e.setId(3L);
        e.setPostId(5L);
        e.setAuthorId(9L);
        e.setStatus(CommentStatus.HIDDEN);
        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        when(commentsRepository.findById(3L)).thenReturn(Optional.of(e));
        when(commentsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(administratorService.findByUsername("admin@example.com")).thenReturn(Optional.empty());

        PostsEntity p = new PostsEntity();
        p.setId(5L);
        p.setTitle("t");
        p.setContent("c");
        when(postsRepository.findById(5L)).thenReturn(Optional.of(p));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin@example.com", "x"));
        svc.updateStatus(3L, okReq);
        verify(sync).scheduleSyncAfterCommit(3L);

        CommentSetDeletedRequest setDeletedRequest = new CommentSetDeletedRequest();
        assertThrows(IllegalArgumentException.class, () -> svc.setDeleted(null, setDeletedRequest));
        assertThrows(IllegalArgumentException.class, () -> svc.setDeleted(3L, null));
        assertThrows(IllegalArgumentException.class, () -> svc.setDeleted(3L, setDeletedRequest));
        when(commentsRepository.findById(4L)).thenReturn(Optional.empty());
        setDeletedRequest.setIsDeleted(true);
        assertThrows(IllegalArgumentException.class, () -> svc.setDeleted(4L, setDeletedRequest));

        CommentsEntity e2 = new CommentsEntity();
        e2.setId(6L);
        e2.setPostId(7L);
        e2.setAuthorId(8L);
        e2.setStatus(CommentStatus.VISIBLE);
        e2.setIsDeleted(false);
        e2.setCreatedAt(LocalDateTime.now());
        e2.setUpdatedAt(LocalDateTime.now());
        when(commentsRepository.findById(6L)).thenReturn(Optional.of(e2));
        when(postsRepository.findById(7L)).thenReturn(Optional.empty());
        svc.setDeleted(6L, setDeletedRequest);

        CommentsEntity e3 = new CommentsEntity();
        e3.setId(10L);
        e3.setPostId(11L);
        e3.setAuthorId(12L);
        e3.setStatus(CommentStatus.HIDDEN);
        e3.setIsDeleted(false);
        e3.setCreatedAt(LocalDateTime.now());
        e3.setUpdatedAt(LocalDateTime.now());
        when(commentsRepository.findById(10L)).thenReturn(Optional.of(e3));
        PostsEntity p2 = new PostsEntity();
        p2.setId(11L);
        p2.setTitle("title");
        p2.setContent("content");
        when(postsRepository.findById(11L)).thenReturn(Optional.of(p2));
        svc.setDeleted(10L, setDeletedRequest);

        verify(auditLogWriter, times(3)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void private_methods_should_cover_remaining_comment_branches() {
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminCommentsServiceImpl svc = buildService(commentsRepository, usersRepository, postsRepository, administratorService);

        Object summary = ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "summarizeForAudit", (Object) null);
        assertTrue(((Map<?, ?>) summary).isEmpty());

        CommentsEntity c = new CommentsEntity();
        c.setId(1L);
        c.setPostId(2L);
        c.setParentId(3L);
        c.setAuthorId(4L);
        c.setStatus(null);
        c.setIsDeleted(null);
        Object summary2 = ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "summarizeForAudit", c);
        assertNull(((Map<?, ?>) summary2).get("status"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) summary2).get("isDeleted"));

        assertNull(ReflectionTestUtils.invokeMethod(svc, "safeAuthorName", (Object) null));
        when(administratorService.findById(11L)).thenThrow(new RuntimeException("x"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "safeAuthorName", 11L));

        assertNull(ReflectionTestUtils.invokeMethod(svc, "parseStatusOrNull", " "));

        SecurityContextHolder.clearContext();
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentActorNameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "x", List.of()));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentActorNameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("   ", "x", List.of()));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentActorNameOrNull"));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("guest@example.com", "x"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull"));
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentActorNameOrNull"));

        Authentication authWithNullName = mock(Authentication.class);
        when(authWithNullName.isAuthenticated()).thenReturn(true);
        when(authWithNullName.getPrincipal()).thenReturn(new Object());
        when(authWithNullName.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authWithNullName);
        assertNull(ReflectionTestUtils.invokeMethod(svc, "currentActorNameOrNull"));

        UsersEntity actor = new UsersEntity();
        actor.setId(200L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice@example.com", "x", List.of()));
        assertEquals(200L, ((Long) ReflectionTestUtils.invokeMethod(svc, "currentUserIdOrNull")).longValue());
        assertEquals("alice@example.com", ReflectionTestUtils.invokeMethod(svc, "currentActorNameOrNull"));

        Object m0 = ReflectionTestUtils.invokeMethod(svc, "loadPostsByIds", (Object) null);
        assertTrue(((Map<?, ?>) m0).isEmpty());
        Object m1 = ReflectionTestUtils.invokeMethod(svc, "loadPostsByIds", new ArrayList<Long>());
        assertTrue(((Map<?, ?>) m1).isEmpty());
        Object m2 = ReflectionTestUtils.invokeMethod(svc, "loadPostsByIds", Arrays.asList((Long) null, null));
        assertTrue(((Map<?, ?>) m2).isEmpty());

        PostsEntity p1 = new PostsEntity();
        p1.setId(5L);
        PostsEntity p2 = new PostsEntity();
        p2.setId(null);
        when(postsRepository.findAllById(anyCollection())).thenReturn(Arrays.asList(p1, null, p2));
        Object m3 = ReflectionTestUtils.invokeMethod(svc, "loadPostsByIds", Arrays.asList(5L, 5L, 6L, null));
        assertEquals(1, ((Map<?, ?>) m3).size());

        CommentsEntity c2 = new CommentsEntity();
        c2.setId(9L);
        c2.setPostId(8L);
        c2.setParentId(7L);
        c2.setAuthorId(6L);
        c2.setStatus(null);
        c2.setIsDeleted(null);
        c2.setCreatedAt(LocalDateTime.now());
        c2.setUpdatedAt(LocalDateTime.now());
        Object dto = ReflectionTestUtils.invokeMethod(AdminCommentsServiceImpl.class, "toAdminDTO", c2, null, null, null);
        assertNull(ReflectionTestUtils.getField(dto, "status"));
        assertEquals(Boolean.FALSE, ReflectionTestUtils.getField(dto, "isDeleted"));
    }

    @SuppressWarnings("unchecked")
    private static void invokeSpecification(Specification<CommentsEntity> spec) {
        Root<CommentsEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Object> path = mock(Path.class);
        Expression<String> strExp = mock(Expression.class);

        when(root.get(anyString())).thenReturn(path);
        when(path.in(anyCollection())).thenReturn(predicate);
        when(cb.equal(any(Expression.class), any())).thenReturn(predicate);
        when(cb.isNull(any(Expression.class))).thenReturn(predicate);
        when(cb.isFalse(any(Expression.class))).thenReturn(predicate);
        when(cb.isTrue(any(Expression.class))).thenReturn(predicate);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.or(any(Predicate[].class))).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.lower(any(Expression.class))).thenReturn(strExp);
        when(query.where(any(Predicate[].class))).thenReturn((CriteriaQuery) query);
        when(query.getRestriction()).thenReturn(predicate);

        spec.toPredicate(root, query, cb);
    }

    private static AdminCommentsServiceImpl buildService(
            CommentsRepository commentsRepository,
            UsersRepository usersRepository,
            PostsRepository postsRepository,
            AdministratorService administratorService
    ) {
        AdminCommentsServiceImpl svc = new AdminCommentsServiceImpl();
        ReflectionTestUtils.setField(svc, "commentsRepository", commentsRepository);
        ReflectionTestUtils.setField(svc, "administratorService", administratorService);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "postsRepository", postsRepository);
        ReflectionTestUtils.setField(svc, "ragCommentIndexVisibilitySyncService", mock(RagCommentIndexVisibilitySyncService.class));
        ReflectionTestUtils.setField(svc, "auditLogWriter", mock(AuditLogWriter.class));
        ReflectionTestUtils.setField(svc, "auditDiffBuilder", mock(AuditDiffBuilder.class));
        return svc;
    }
}
