package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.HotScoresEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.HotScoresRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostViewsDailyRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortalPostsServiceImplBranchUnitTest {

    @Mock
    PostsService postsService;

    @Mock
    PostInteractionsService postInteractionsService;

    @Mock
    CommentsService commentsService;

    @Mock
    PostViewsDailyRepository postViewsDailyRepository;

    @Mock
    HotScoresRepository hotScoresRepository;

    @Mock
    ReactionsRepository reactionsRepository;

    @Mock
    AdministratorService administratorService;

    @Mock
    UsersRepository usersRepository;

    @Mock
    BoardsRepository boardsRepository;

    @Mock
    BoardAccessControlService boardAccessControlService;

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
    }

    private PortalPostsServiceImpl newService(HotScoresRepository hotScoresRepositoryOrNull) {
        PortalPostsServiceImpl s = new PortalPostsServiceImpl();
        setField(s, "postsService", postsService);
        setField(s, "postInteractionsService", postInteractionsService);
        setField(s, "commentsService", commentsService);
        setField(s, "postViewsDailyRepository", postViewsDailyRepository);
        setField(s, "hotScoresRepository", hotScoresRepositoryOrNull);
        setField(s, "reactionsRepository", reactionsRepository);
        setField(s, "administratorService", administratorService);
        setField(s, "usersRepository", usersRepository);
        setField(s, "boardsRepository", boardsRepository);
        setField(s, "boardAccessControlService", boardAccessControlService);
        return s;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invokeStatic(Class<?> cls, String name, Class<?>[] types, Object[] args) {
        try {
            Method m = cls.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object invokeInstance(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static PostsEntity post(long id, Long boardId, Long authorId, PostStatus status) {
        PostsEntity e = new PostsEntity();
        e.setId(id);
        e.setTenantId(1L);
        e.setBoardId(boardId);
        e.setAuthorId(authorId);
        e.setTitle("t");
        e.setContent("c");
        e.setContentFormat(null);
        e.setStatus(status);
        e.setMetadata(Map.of());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setPublishedAt(LocalDateTime.now());
        return e;
    }

    @Test
    void readProfileString_shouldHandleNullsAndTrim() {
        assertNull(invokeStatic(PortalPostsServiceImpl.class, "readProfileString",
                new Class<?>[]{UsersEntity.class, String.class},
                new Object[]{null, "avatarUrl"}));

        UsersEntity u1 = new UsersEntity();
        u1.setMetadata(null);
        assertNull(invokeStatic(PortalPostsServiceImpl.class, "readProfileString",
                new Class<?>[]{UsersEntity.class, String.class},
                new Object[]{u1, "avatarUrl"}));

        UsersEntity u2 = new UsersEntity();
        u2.setMetadata(Map.of("profile", "x"));
        assertNull(invokeStatic(PortalPostsServiceImpl.class, "readProfileString",
                new Class<?>[]{UsersEntity.class, String.class},
                new Object[]{u2, "avatarUrl"}));

        UsersEntity u3 = new UsersEntity();
        var profile = new java.util.HashMap<String, Object>();
        profile.put("avatarUrl", null);
        u3.setMetadata(Map.of("profile", profile));
        assertNull(invokeStatic(PortalPostsServiceImpl.class, "readProfileString",
                new Class<?>[]{UsersEntity.class, String.class},
                new Object[]{u3, "avatarUrl"}));

        UsersEntity u4 = new UsersEntity();
        u4.setMetadata(Map.of("profile", Map.of("avatarUrl", "   ")));
        assertNull(invokeStatic(PortalPostsServiceImpl.class, "readProfileString",
                new Class<?>[]{UsersEntity.class, String.class},
                new Object[]{u4, "avatarUrl"}));

        UsersEntity u5 = new UsersEntity();
        u5.setMetadata(Map.of("profile", Map.of("avatarUrl", "  http://a  ")));
        assertEquals("http://a", invokeStatic(PortalPostsServiceImpl.class, "readProfileString",
                new Class<?>[]{UsersEntity.class, String.class},
                new Object[]{u5, "avatarUrl"}));
    }

    @Test
    void query_shouldFilterByBoardVisibility_andCoverDisplayBranches() {
        PortalPostsServiceImpl s = newService(null);

        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(1L));
        when(boardAccessControlService.canViewBoard(eq(10L), anySet())).thenReturn(true);
        when(boardAccessControlService.canViewBoard(eq(20L), anySet())).thenReturn(false);

        PostsEntity a = post(1L, null, 1L, PostStatus.PUBLISHED);
        PostsEntity b = post(2L, 10L, 2L, PostStatus.PUBLISHED);
        PostsEntity c = post(3L, 20L, 3L, PostStatus.PUBLISHED);
        PostsEntity d = post(4L, 10L, null, PostStatus.PUBLISHED);

        when(postsService.query(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(a, b, c, d), PageRequest.of(0, 10), 4));

        UsersEntity au = new UsersEntity();
        au.setId(1L);
        au.setUsername(null);
        au.setMetadata(null);

        UsersEntity bu = new UsersEntity();
        bu.setId(2L);
        bu.setUsername("  Alice  ");
        bu.setMetadata(Map.of("profile", Map.of("avatarUrl", "  http://img  ")));

        when(usersRepository.findByIdInAndIsDeletedFalse(any())).thenReturn(List.of(au, bu));

        BoardsEntity board10 = new BoardsEntity();
        board10.setId(10L);
        board10.setName("  Board  ");
        when(boardsRepository.findAllById(any())).thenReturn(List.of(board10));

        when(commentsService.countByPostId(anyLong())).thenReturn(1L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(2L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(3L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        var page = s.query(null, null, null, null, null, null, null, null, 1, 10, null, null);
        assertEquals(3, page.getContent().size());
        assertEquals(List.of(1L, 2L, 4L), page.getContent().stream().map(PostDetailDTO::getId).toList());

        PostDetailDTO dtoA = page.getContent().get(0);
        assertNull(dtoA.getBoardName());
        assertNull(dtoA.getAuthorName());

        PostDetailDTO dtoB = page.getContent().get(1);
        assertEquals("Board", dtoB.getBoardName());
        assertEquals("Alice", dtoB.getAuthorName());
        assertEquals("http://img", dtoB.getAuthorAvatarUrl());

        PostDetailDTO dtoD = page.getContent().get(2);
        assertEquals("Board", dtoD.getBoardName());
        assertNull(dtoD.getAuthorName());
    }

    @Test
    void query_whenUserOrBoardListsContainNulls_shouldNotFail() {
        PortalPostsServiceImpl s = newService(null);

        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(1L));
        when(boardAccessControlService.canViewBoard(eq(10L), anySet())).thenReturn(true);

        PostsEntity a = post(1L, 10L, 1L, PostStatus.PUBLISHED);
        when(postsService.query(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageImpl<>(java.util.Arrays.asList(null, a), PageRequest.of(0, 10), 2));

        UsersEntity uNoId = new UsersEntity();
        uNoId.setId(null);
        when(usersRepository.findByIdInAndIsDeletedFalse(any())).thenReturn(java.util.Arrays.asList(null, uNoId));
        when(boardsRepository.findAllById(any())).thenReturn(null);

        when(commentsService.countByPostId(anyLong())).thenReturn(0L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(0L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(0L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        var page = s.query(null, null, null, null, null, null, null, null, 1, 10, null, null);
        assertEquals(1, page.getContent().size());
        assertNull(page.getContent().get(0).getAuthorName());
        assertNull(page.getContent().get(0).getBoardName());
    }

    @Test
    void enrichAggregates_shouldHandleHotScoreAndLikedFavoriteExceptions() {
        PortalPostsServiceImpl s = newService(hotScoresRepository);

        when(commentsService.countByPostId(anyLong())).thenReturn(10L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(20L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(30L);

        HotScoresEntity hs = new HotScoresEntity();
        hs.setPostId(1L);
        hs.setScoreAll(99.0);
        hs.setScore24h(1.0);
        hs.setScore7d(2.0);
        hs.setDecayBase(1.0);
        hs.setLastRecalculatedAt(LocalDateTime.now());

        when(hotScoresRepository.findByPostId(1L)).thenReturn(Optional.of(hs));
        when(hotScoresRepository.findByPostId(2L)).thenThrow(new RuntimeException("x"));

        when(postInteractionsService.likedByMe(1L)).thenReturn(true);
        when(postInteractionsService.likedByMe(2L)).thenThrow(new RuntimeException("x"));
        when(postInteractionsService.favoritedByMe(1L)).thenThrow(new RuntimeException("x"));
        when(postInteractionsService.favoritedByMe(2L)).thenReturn(true);

        PostDetailDTO dto1 = new PostDetailDTO();
        dto1.setId(1L);
        PostDetailDTO r1 = (PostDetailDTO) invokeInstance(s, "enrichAggregates", new Class<?>[]{PostDetailDTO.class}, new Object[]{dto1});
        assertEquals(99.0, r1.getHotScore());
        assertTrue(r1.getLikedByMe());
        assertFalse(r1.getFavoritedByMe());

        PostDetailDTO dto2 = new PostDetailDTO();
        dto2.setId(2L);
        PostDetailDTO r2 = (PostDetailDTO) invokeInstance(s, "enrichAggregates", new Class<?>[]{PostDetailDTO.class}, new Object[]{dto2});
        assertNull(r2.getHotScore());
        assertFalse(r2.getLikedByMe());
        assertTrue(r2.getFavoritedByMe());
    }

    @Test
    void enrichAggregates_whenHotScoresRepoNull_shouldSkipHotScoreBranch() {
        PortalPostsServiceImpl s = newService(null);

        when(commentsService.countByPostId(anyLong())).thenReturn(0L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(0L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(0L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        PostDetailDTO dto = new PostDetailDTO();
        dto.setId(1L);
        PostDetailDTO r = (PostDetailDTO) invokeInstance(s, "enrichAggregates", new Class<?>[]{PostDetailDTO.class}, new Object[]{dto});
        assertNull(r.getHotScore());
    }

    @Test
    void getById_whenNotPublishedAndNotAuthor_shouldThrow() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenNotPublishedAndAuthenticatedAuthor_shouldReturn() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.of(me));

        when(commentsService.countByPostId(anyLong())).thenReturn(1L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(2L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(3L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(true);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(true);

        PostDetailDTO dto = s.getById(9L);
        assertEquals(9L, dto.getId());
    }

    @Test
    void getById_whenNotPublishedAndNotAuthenticated_shouldThrow() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenNotPublishedAndAnonymous_shouldThrow() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("anonymousUser", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenNotPublishedAndUserLookupEmpty_shouldThrow() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenNotPublishedAndAuthenticatedButNotAuthor_shouldThrow() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UsersEntity me = new UsersEntity();
        me.setId(8L);
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.of(me));

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenNotPublishedAndAuthorIdNull_shouldThrow() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, null, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UsersEntity me = new UsersEntity();
        me.setId(7L);
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.of(me));

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenPublished_shouldIncrementViews_andEnforceBoardPermission() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, 10L, 7L, PostStatus.PUBLISHED);
        when(postsService.getById(9L)).thenReturn(e);
        doThrow(new RuntimeException("x")).when(postViewsDailyRepository).increment(eq(9L), any());

        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(1L));
        when(boardAccessControlService.canViewBoard(eq(10L), anySet())).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> s.getById(9L));
        verify(postViewsDailyRepository).increment(eq(9L), any());
    }

    @Test
    void getById_whenPublishedAndBoardAllowed_shouldLoadAuthorAndBoard() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, 10L, 7L, PostStatus.PUBLISHED);
        when(postsService.getById(9L)).thenReturn(e);

        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(1L));
        when(boardAccessControlService.canViewBoard(eq(10L), anySet())).thenReturn(true);

        UsersEntity author = new UsersEntity();
        author.setId(7L);
        author.setUsername("   ");
        author.setMetadata(Map.of("profile", Map.of("avatarUrl", "  ")));
        when(usersRepository.findByIdAndIsDeletedFalse(7L)).thenReturn(Optional.of(author));

        BoardsEntity board = new BoardsEntity();
        board.setId(10L);
        board.setName(null);
        when(boardsRepository.findById(10L)).thenReturn(Optional.of(board));

        when(commentsService.countByPostId(anyLong())).thenReturn(0L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(0L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(0L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        PostDetailDTO dto = s.getById(9L);
        assertNull(dto.getAuthorName());
        assertNull(dto.getAuthorAvatarUrl());
        assertNull(dto.getBoardName());
    }

    @Test
    void getById_whenNotPublishedAndAdminLookupThrows_shouldTreatAsNotAuthor() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, null, 7L, PostStatus.DRAFT);
        when(postsService.getById(9L)).thenReturn(e);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(administratorService.findByUsername(anyString())).thenThrow(new RuntimeException("x"));

        assertThrows(IllegalArgumentException.class, () -> s.getById(9L));
    }

    @Test
    void getById_whenPublishedAndRepoThrows_shouldSwallowAndReturn() {
        PortalPostsServiceImpl s = newService(null);

        PostsEntity e = post(9L, 10L, 7L, PostStatus.PUBLISHED);
        when(postsService.getById(9L)).thenReturn(e);

        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(1L));
        when(boardAccessControlService.canViewBoard(eq(10L), anySet())).thenReturn(true);

        when(usersRepository.findByIdAndIsDeletedFalse(7L)).thenThrow(new RuntimeException("x"));
        when(boardsRepository.findById(10L)).thenThrow(new RuntimeException("x"));

        when(commentsService.countByPostId(anyLong())).thenReturn(0L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(0L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(0L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        PostDetailDTO dto = s.getById(9L);
        assertNull(dto.getAuthorName());
        assertNull(dto.getBoardName());
    }

    @Test
    void queryMyBookmarkedPosts_whenAuthNull_shouldThrowAuthenticationException() {
        PortalPostsServiceImpl s = newService(null);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> s.queryMyBookmarkedPosts(1, 10));
    }

    @Test
    void queryMyBookmarkedPosts_whenNotAuthenticated_shouldThrowAuthenticationException() {
        PortalPostsServiceImpl s = newService(null);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p");
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> s.queryMyBookmarkedPosts(1, 10));
    }

    @Test
    void queryMyBookmarkedPosts_whenAnonymousPrincipal_shouldThrowAuthenticationException() {
        PortalPostsServiceImpl s = newService(null);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("anonymousUser", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> s.queryMyBookmarkedPosts(1, 10));
    }

    @Test
    void queryMyBookmarkedPosts_whenUserMissing_shouldThrowIllegalArgumentException() {
        PortalPostsServiceImpl s = newService(null);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(administratorService.findByUsername(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> s.queryMyBookmarkedPosts(1, 10));
    }

    @Test
    void queryMyBookmarkedPosts_whenOk_shouldApplySafePaging_andHandleRepoExceptions() {
        PortalPostsServiceImpl s = newService(null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UsersEntity me = new UsersEntity();
        me.setId(1L);
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.of(me));

        PostsEntity p1 = post(1L, null, null, PostStatus.PUBLISHED);
        PostsEntity p2 = post(2L, 10L, 2L, PostStatus.PUBLISHED);
        when(reactionsRepository.findBookmarkedPostsByUserId(eq(1L), eq(ReactionTargetType.POST), eq(ReactionType.FAVORITE), any()))
                .thenReturn(new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 20), 2));

        when(usersRepository.findByIdAndIsDeletedFalse(2L)).thenThrow(new RuntimeException("x"));
        when(boardsRepository.findById(10L)).thenThrow(new RuntimeException("x"));

        when(commentsService.countByPostId(anyLong())).thenReturn(0L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(0L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(0L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        var page = s.queryMyBookmarkedPosts(0, 0);
        assertEquals(2, page.getContent().size());
        assertEquals(List.of(1L, 2L), page.getContent().stream().map(PostDetailDTO::getId).toList());
        assertNull(page.getContent().get(0).getAuthorName());
        assertNull(page.getContent().get(1).getBoardName());
    }

    @Test
    void queryMyBookmarkedPosts_whenOk_shouldResolveAuthorAndBoard() {
        PortalPostsServiceImpl s = newService(null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", "p", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        UsersEntity me = new UsersEntity();
        me.setId(1L);
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.of(me));

        PostsEntity p1 = post(1L, 10L, 2L, PostStatus.PUBLISHED);
        when(reactionsRepository.findBookmarkedPostsByUserId(eq(1L), eq(ReactionTargetType.POST), eq(ReactionType.FAVORITE), any()))
                .thenReturn(new PageImpl<>(List.of(p1), PageRequest.of(0, 20), 1));

        UsersEntity author = new UsersEntity();
        author.setId(2L);
        author.setUsername("Bob");
        author.setMetadata(Map.of("profile", Map.of("avatarUrl", "http://a")));
        when(usersRepository.findByIdAndIsDeletedFalse(2L)).thenReturn(Optional.of(author));

        BoardsEntity board = new BoardsEntity();
        board.setId(10L);
        board.setName("   ");
        when(boardsRepository.findById(10L)).thenReturn(Optional.of(board));

        when(commentsService.countByPostId(anyLong())).thenReturn(0L);
        when(postInteractionsService.countLikes(anyLong())).thenReturn(0L);
        when(postInteractionsService.countFavorites(anyLong())).thenReturn(0L);
        when(postInteractionsService.likedByMe(anyLong())).thenReturn(false);
        when(postInteractionsService.favoritedByMe(anyLong())).thenReturn(false);

        var page = s.queryMyBookmarkedPosts(1, 20);
        assertEquals(1, page.getContent().size());
        assertEquals("Bob", page.getContent().get(0).getAuthorName());
        assertNull(page.getContent().get(0).getBoardName());
    }
}
