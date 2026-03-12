package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.CommentToggleResponseDTO;
import com.example.EnterpriseRagCommunity.dto.content.HotPostDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PortalSearchService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentControllersBranchUnitTest {

    @Mock
    ReactionsRepository reactionsRepository;
    @Mock
    AdministratorService administratorService;
    @Mock
    AuditLogWriter auditLogWriter;
    @Mock
    PostsService postsService;
    @Mock
    PortalPostsService portalPostsService;
    @Mock
    HotScoresService hotScoresService;
    @Mock
    PortalSearchService portalSearchService;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commentInteractions_shouldCoverLikeUnlikeAndExceptionBranches() {
        CommentInteractionsController c = new CommentInteractionsController();
        setField(c, "reactionsRepository", reactionsRepository);
        setField(c, "administratorService", administratorService);
        setField(c, "auditLogWriter", auditLogWriter);

        assertThrows(IllegalArgumentException.class, () -> c.toggleLike(null));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.com", "p", List.of()));
        UsersEntity me = new UsersEntity();
        me.setId(1L);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(me));
        when(reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(1L, ReactionTargetType.COMMENT, 9L, ReactionType.LIKE)).thenReturn(false);
        when(reactionsRepository.countByTargetTypeAndTargetIdAndType(ReactionTargetType.COMMENT, 9L, ReactionType.LIKE)).thenReturn(3L);

        CommentToggleResponseDTO liked = c.toggleLike(9L);
        assertTrue(liked.isLikedByMe());
        assertEquals(3L, liked.getLikeCount());
        verify(reactionsRepository).save(any(ReactionsEntity.class));
        verify(auditLogWriter).write(eq(1L), eq("u@example.com"), eq("COMMENT_LIKE_TOGGLE"), eq("COMMENT"), eq(9L), eq(AuditResult.SUCCESS), eq("点赞评论"), isNull(), anyMap());

        when(reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(1L, ReactionTargetType.COMMENT, 10L, ReactionType.LIKE)).thenReturn(true);
        when(reactionsRepository.countByTargetTypeAndTargetIdAndType(ReactionTargetType.COMMENT, 10L, ReactionType.LIKE)).thenReturn(2L);
        CommentToggleResponseDTO unliked = c.toggleLike(10L);
        assertFalse(unliked.isLikedByMe());
        assertEquals(2L, unliked.getLikeCount());
        verify(reactionsRepository).deleteByUserIdAndTargetTypeAndTargetIdAndType(1L, ReactionTargetType.COMMENT, 10L, ReactionType.LIKE);

        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> c.toggleLike(11L));
        verify(auditLogWriter).write(isNull(), isNull(), eq("COMMENT_LIKE_TOGGLE"), eq("COMMENT"), eq(11L), eq(AuditResult.FAIL), anyString(), isNull(), anyMap());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u3@example.com", "p"));
        assertThrows(RuntimeException.class, () -> c.toggleLike(111L));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("missing@example.com", "p", List.of()));
        when(administratorService.findByUsername("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> c.toggleLike(12L));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u2@example.com", "p", List.of()));
        UsersEntity me2 = new UsersEntity();
        me2.setId(2L);
        when(administratorService.findByUsername("u2@example.com")).thenReturn(Optional.of(me2));
        when(reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(2L, ReactionTargetType.COMMENT, 13L, ReactionType.LIKE))
                .thenThrow(new RuntimeException("db"));
        assertThrows(RuntimeException.class, () -> c.toggleLike(13L));
    }

    @Test
    void postsController_shouldCoverStatusAndMineBranches() {
        PostsController c = new PostsController();
        setField(c, "postsService", postsService);
        setField(c, "portalPostsService", portalPostsService);
        setField(c, "administratorService", administratorService);

        when(portalPostsService.query(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        c.list(null, null, null, null, null, null, null, null, 1, 20, null, null);
        ArgumentCaptor<PostStatus> statusCaptor = ArgumentCaptor.forClass(PostStatus.class);
        verify(portalPostsService).query(any(), any(), any(), any(), statusCaptor.capture(), any(), any(), any(), anyInt(), anyInt(), any(), any());
        assertEquals(PostStatus.PUBLISHED, statusCaptor.getValue());

        c.list(null, null, null, null, "ALL", null, null, null, 1, 20, null, null);
        c.list(null, null, null, null, "DRAFT", null, null, null, 1, 20, null, null);
        c.list(null, null, null, null, "   ", null, null, null, 1, 20, null, null);
        verify(portalPostsService, times(4)).query(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
        assertThrows(IllegalArgumentException.class, () -> c.list(null, null, null, null, "NOT_EXISTS", null, null, null, 1, 20, null, null));

        ResponseStatusException e1 = assertThrows(ResponseStatusException.class, () -> c.listMine(null, null, null, null, null, null, null, 1, 20, null, null));
        assertEquals(HttpStatus.UNAUTHORIZED, e1.getStatusCode());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.com", "p"));
        ResponseStatusException e2 = assertThrows(ResponseStatusException.class, () -> c.listMine(null, null, null, null, null, null, null, 1, 20, null, null));
        assertEquals(HttpStatus.UNAUTHORIZED, e2.getStatusCode());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "p", List.of()));
        ResponseStatusException e3 = assertThrows(ResponseStatusException.class, () -> c.listMine(null, null, null, null, null, null, null, 1, 20, null, null));
        assertEquals(HttpStatus.UNAUTHORIZED, e3.getStatusCode());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u2@example.com", "p", List.of()));
        when(administratorService.findByUsername("u2@example.com")).thenReturn(Optional.empty());
        ResponseStatusException e4 = assertThrows(ResponseStatusException.class, () -> c.listMine(null, null, null, null, null, null, null, 1, 20, null, null));
        assertEquals(HttpStatus.UNAUTHORIZED, e4.getStatusCode());

        UsersEntity me = new UsersEntity();
        me.setId(88L);
        when(administratorService.findByUsername("u2@example.com")).thenReturn(Optional.of(me));
        c.listMine(null, null, null, null, "ALL", null, null, 1, 20, null, null);
        c.listMine(null, null, null, null, "PUBLISHED", null, null, 1, 20, null, null);
        c.listMine(null, null, null, null, "   ", null, null, 1, 20, null, null);
        c.listMine(null, null, null, null, "all", null, null, 1, 20, null, null);
        verify(portalPostsService, atLeast(8)).query(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void hotScoresController_shouldCoverParseWindowBranches() {
        HotScoresController c = new HotScoresController();
        setField(c, "hotScoresService", hotScoresService);
        when(hotScoresService.listHot(any(), anyInt(), anyInt())).thenReturn(new PageImpl<HotPostDTO>(List.of(), PageRequest.of(0, 20), 0));

        c.listHot(null, 1, 20);
        c.listHot("24h", 1, 20);
        c.listHot("7d", 1, 20);
        c.listHot("all", 1, 20);
        assertThrows(IllegalArgumentException.class, () -> c.listHot("x", 1, 20));

        verify(hotScoresService, times(4)).listHot(any(), eq(1), eq(20));
    }

    @Test
    void portalSearchController_shouldCoverQFallbackBranch() {
        PortalSearchController c = new PortalSearchController();
        setField(c, "portalSearchService", portalSearchService);
        when(portalSearchService.search(any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.<com.example.EnterpriseRagCommunity.dto.content.PortalSearchHitDTO>of(), PageRequest.of(0, 20), 0));

        c.search("query", "keyword", 1L, 1, 20);
        c.search("   ", "keyword", 2L, 1, 20);
        c.search(null, "keyword2", 3L, 1, 20);
        c.search(null, null, 4L, 1, 20);

        verify(portalSearchService).search(eq("query"), eq(1L), eq(1), eq(20));
        verify(portalSearchService).search(eq("keyword"), eq(2L), eq(1), eq(20));
        verify(portalSearchService).search(eq("keyword2"), eq(3L), eq(1), eq(20));
        verify(portalSearchService).search(isNull(), eq(4L), eq(1), eq(20));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
