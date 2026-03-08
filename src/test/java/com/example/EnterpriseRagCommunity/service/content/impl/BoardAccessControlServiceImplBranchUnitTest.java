package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.BoardAccessControlDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardModeratorsEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardRolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.BoardRolePermissionType;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardRolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.impl.BoardAccessControlServiceImpl;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardAccessControlServiceImplBranchUnitTest {

    @Mock
    BoardsRepository boardsRepository;

    @Mock
    BoardRolePermissionsRepository boardRolePermissionsRepository;

    @Mock
    BoardModeratorsRepository boardModeratorsRepository;

    @Mock
    UserRoleLinksRepository userRoleLinksRepository;

    @Mock
    UsersRepository usersRepository;

    @Mock
    AdministratorService administratorService;

    @Mock
    AuditLogWriter auditLogWriter;

    @Captor
    ArgumentCaptor<BoardRolePermissionsEntity> permCaptor;

    @Captor
    ArgumentCaptor<BoardModeratorsEntity> modCaptor;

    @AfterEach
    void afterEach() {
        SecurityContextTestSupport.clear();
    }

    private BoardAccessControlServiceImpl newService() {
        return new BoardAccessControlServiceImpl(
                boardsRepository,
                boardRolePermissionsRepository,
                boardModeratorsRepository,
                userRoleLinksRepository,
                usersRepository,
                administratorService,
                auditLogWriter
        );
    }

    @Test
    void getByBoardId_whenBoardIdNull_shouldThrow() {
        BoardAccessControlServiceImpl s = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> s.getByBoardId(null));
        assertEquals("boardId 不能为空", ex.getMessage());
    }

    @Test
    void getByBoardId_whenBoardMissing_shouldThrow() {
        BoardAccessControlServiceImpl s = newService();
        when(boardsRepository.existsById(9L)).thenReturn(false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> s.getByBoardId(9L));
        assertEquals("版块不存在: 9", ex.getMessage());
    }

    @Test
    void getByBoardId_whenExists_shouldFilterNullNonPositiveAndDistinct() {
        BoardAccessControlServiceImpl s = newService();
        when(boardsRepository.existsById(1L)).thenReturn(true);
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW))
                .thenReturn(Arrays.asList(null, 0L, -1L, 2L, 2L, 3L));
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST))
                .thenReturn(null);
        when(boardModeratorsRepository.findUserIdsByBoardId(1L))
                .thenReturn(Arrays.asList(5L, null, 5L, 6L, -2L));

        BoardAccessControlDTO dto = s.getByBoardId(1L);
        assertEquals(1L, dto.getBoardId());
        assertEquals(List.of(2L, 3L), dto.getViewRoleIds());
        assertEquals(List.of(), dto.getPostRoleIds());
        assertEquals(List.of(5L, 6L), dto.getModeratorUserIds());
    }

    @Test
    void replace_whenBoardIdNull_shouldThrow() {
        BoardAccessControlServiceImpl s = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> s.replace(null, null));
        assertEquals("boardId 不能为空", ex.getMessage());
    }

    @Test
    void replace_whenBoardMissing_shouldThrow() {
        BoardAccessControlServiceImpl s = newService();
        when(boardsRepository.existsById(9L)).thenReturn(false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> s.replace(9L, null));
        assertEquals("版块不存在: 9", ex.getMessage());
    }

    @Test
    void replace_whenDtoNull_shouldDeleteAndReturnSaved_andSwallowAuditException() {
        BoardAccessControlServiceImpl s = newService();
        when(boardsRepository.existsById(1L)).thenReturn(true);
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW)).thenReturn(List.of());
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST)).thenReturn(List.of());
        when(boardModeratorsRepository.findUserIdsByBoardId(1L)).thenReturn(List.of());

        doThrow(new RuntimeException("boom")).when(auditLogWriter).write(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
        );

        BoardAccessControlDTO out = assertDoesNotThrow(() -> s.replace(1L, null));
        assertEquals(1L, out.getBoardId());

        verify(boardRolePermissionsRepository).deleteByBoardId(1L);
        verify(boardModeratorsRepository).deleteByBoardId(1L);
        verify(boardRolePermissionsRepository, never()).save(any());
        verify(boardModeratorsRepository, never()).save(any());
    }

    @Test
    void replace_whenModeratorUserMissing_shouldThrowBeforeDelete() {
        BoardAccessControlServiceImpl s = newService();
        when(boardsRepository.existsById(1L)).thenReturn(true);

        BoardAccessControlDTO dto = new BoardAccessControlDTO();
        dto.setModeratorUserIds(List.of(10L, 11L));

        UsersEntity u10 = new UsersEntity();
        u10.setId(10L);
        when(usersRepository.findByIdInAndIsDeletedFalse(any(LinkedHashSet.class))).thenReturn(List.of(u10));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> s.replace(1L, dto));
        assertTrue(ex.getMessage().contains("版主用户不存在或已删除"));
        assertTrue(ex.getMessage().contains("11"));

        verify(boardRolePermissionsRepository, never()).deleteByBoardId(anyLong());
        verify(boardModeratorsRepository, never()).deleteByBoardId(anyLong());
        verify(boardRolePermissionsRepository, never()).save(any());
        verify(boardModeratorsRepository, never()).save(any());
        verify(auditLogWriter, never()).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void replace_whenNormal_shouldNormalizeSaveAndWriteAudit() {
        BoardAccessControlServiceImpl s = newService();
        when(boardsRepository.existsById(1L)).thenReturn(true);

        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW))
                .thenReturn(List.of(1L));
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST))
                .thenReturn(List.of(2L));
        when(boardModeratorsRepository.findUserIdsByBoardId(1L))
                .thenReturn(List.of(3L));

        UsersEntity u5 = new UsersEntity();
        u5.setId(5L);
        UsersEntity u6 = new UsersEntity();
        u6.setId(6L);
        when(usersRepository.findByIdInAndIsDeletedFalse(any(LinkedHashSet.class))).thenReturn(List.of(u5, u6));

        when(boardRolePermissionsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(boardModeratorsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UsersEntity actor = new UsersEntity();
        actor.setId(99L);
        SecurityContextTestSupport.setAuthenticatedEmail("a@b.com");
        when(administratorService.findByUsername("a@b.com")).thenReturn(Optional.of(actor));

        BoardAccessControlDTO dto = new BoardAccessControlDTO();
        dto.setViewRoleIds(Arrays.asList(null, 0L, 2L, 2L, 3L));
        dto.setPostRoleIds(List.of(-1L, 4L, 4L));
        dto.setModeratorUserIds(Arrays.asList(null, -2L, 5L, 6L, 5L));

        BoardAccessControlDTO out = s.replace(1L, dto);
        assertEquals(1L, out.getBoardId());

        verify(boardRolePermissionsRepository).deleteByBoardId(1L);
        verify(boardModeratorsRepository).deleteByBoardId(1L);

        verify(boardRolePermissionsRepository, times(3)).save(permCaptor.capture());
        List<BoardRolePermissionsEntity> perms = permCaptor.getAllValues();
        assertEquals(3, perms.size());
        assertTrue(perms.stream().allMatch(p -> p.getBoardId().equals(1L)));
        assertEquals(Set.of(BoardRolePermissionType.VIEW, BoardRolePermissionType.POST),
                new LinkedHashSet<>(perms.stream().map(BoardRolePermissionsEntity::getPerm).toList()));

        long viewCount = perms.stream().filter(p -> p.getPerm() == BoardRolePermissionType.VIEW).count();
        long postCount = perms.stream().filter(p -> p.getPerm() == BoardRolePermissionType.POST).count();
        assertEquals(2, viewCount);
        assertEquals(1, postCount);

        verify(boardModeratorsRepository, times(2)).save(modCaptor.capture());
        List<BoardModeratorsEntity> mods = modCaptor.getAllValues();
        assertEquals(Set.of(5L, 6L), new LinkedHashSet<>(mods.stream().map(BoardModeratorsEntity::getUserId).toList()));

        verify(auditLogWriter).write(
                eq(99L),
                eq("a@b.com"),
                eq("BOARD_ACCESS_CONTROL_REPLACE"),
                eq("BOARD"),
                eq(1L),
                eq(AuditResult.SUCCESS),
                isNull(),
                isNull(),
                any(Map.class)
        );
    }

    @Test
    void currentUserRoleIds_whenAuthNull_shouldReturnEmpty() {
        BoardAccessControlServiceImpl s = newService();
        SecurityContextHolder.clearContext();
        assertEquals(Set.of(), s.currentUserRoleIds());
    }

    @Test
    void currentUserRoleIds_whenNotAuthenticated_shouldReturnEmpty() {
        BoardAccessControlServiceImpl s = newService();
        TestingAuthenticationToken auth = new TestingAuthenticationToken("a@b.com", "n/a");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertEquals(Set.of(), s.currentUserRoleIds());
    }

    @Test
    void currentUserRoleIds_whenAnonymousUser_shouldReturnEmpty() {
        BoardAccessControlServiceImpl s = newService();
        TestingAuthenticationToken auth = new TestingAuthenticationToken("anonymousUser", "n/a");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertEquals(Set.of(), s.currentUserRoleIds());
    }

    @Test
    void currentUserRoleIds_whenUserMissing_shouldReturnEmpty() {
        BoardAccessControlServiceImpl s = newService();
        SecurityContextTestSupport.setAuthenticatedEmail("missing@b.com");
        when(administratorService.findByUsername("missing@b.com")).thenReturn(Optional.empty());
        assertEquals(Set.of(), s.currentUserRoleIds());
    }

    @Test
    void currentUserRoleIds_whenLinksNullOrEmpty_shouldReturnEmpty() {
        BoardAccessControlServiceImpl s = newService();
        SecurityContextTestSupport.setAuthenticatedEmail("a@b.com");
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        when(administratorService.findByUsername("a@b.com")).thenReturn(Optional.of(u));

        when(userRoleLinksRepository.findByUserId(1L)).thenReturn(null);
        assertEquals(Set.of(), s.currentUserRoleIds());

        when(userRoleLinksRepository.findByUserId(1L)).thenReturn(List.of());
        assertEquals(Set.of(), s.currentUserRoleIds());
    }

    @Test
    void currentUserRoleIds_whenLinksContainNullAndNullRoleId_shouldFilterAndDedup() {
        BoardAccessControlServiceImpl s = newService();
        SecurityContextTestSupport.setAuthenticatedEmail("a@b.com");
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        when(administratorService.findByUsername("a@b.com")).thenReturn(Optional.of(u));

        UserRoleLinksEntity rNull = new UserRoleLinksEntity();
        rNull.setUserId(1L);
        rNull.setRoleId(null);

        UserRoleLinksEntity r2a = new UserRoleLinksEntity();
        r2a.setUserId(1L);
        r2a.setRoleId(2L);

        UserRoleLinksEntity r2b = new UserRoleLinksEntity();
        r2b.setUserId(1L);
        r2b.setRoleId(2L);

        UserRoleLinksEntity r1 = new UserRoleLinksEntity();
        r1.setUserId(1L);
        r1.setRoleId(1L);

        when(userRoleLinksRepository.findByUserId(1L)).thenReturn(Arrays.asList(null, rNull, r2a, r2b, r1));

        assertEquals(new LinkedHashSet<>(List.of(2L, 1L)), s.currentUserRoleIds());
    }

    @Test
    void canViewAndPost_whenBoardIdNull_shouldFalse() {
        BoardAccessControlServiceImpl s = newService();
        assertFalse(s.canViewBoard(null, Set.of(1L)));
        assertFalse(s.canPostBoard(null, Set.of(1L)));
    }

    @Test
    void canViewAndPost_whenRequiredNull_shouldTrue() {
        BoardAccessControlServiceImpl s = newService();
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW)).thenReturn(null);
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST)).thenReturn(null);
        assertTrue(s.canViewBoard(1L, null));
        assertTrue(s.canPostBoard(1L, null));
    }

    @Test
    void canViewAndPost_whenRequiredEmpty_shouldTrue() {
        BoardAccessControlServiceImpl s = newService();
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW)).thenReturn(List.of());
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST)).thenReturn(List.of());
        assertTrue(s.canViewBoard(1L, Set.of()));
        assertTrue(s.canPostBoard(1L, Set.of()));
    }

    @Test
    void canViewAndPost_whenRoleIdsNullOrEmpty_shouldFalse() {
        BoardAccessControlServiceImpl s = newService();
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW)).thenReturn(List.of(2L));
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST)).thenReturn(List.of(2L));

        assertFalse(s.canViewBoard(1L, null));
        assertFalse(s.canPostBoard(1L, null));
        assertFalse(s.canViewBoard(1L, Set.of()));
        assertFalse(s.canPostBoard(1L, Set.of()));
    }

    @Test
    void canViewAndPost_whenHitAndNullRid_shouldTrue() {
        BoardAccessControlServiceImpl s = newService();
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW)).thenReturn(Arrays.asList(null, 2L));
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST)).thenReturn(Arrays.asList(null, 2L));
        assertTrue(s.canViewBoard(1L, Set.of(2L)));
        assertTrue(s.canPostBoard(1L, Set.of(2L)));
    }

    @Test
    void canViewAndPost_whenNoHitOrOnlyNullRid_shouldFalse() {
        BoardAccessControlServiceImpl s = newService();
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.VIEW)).thenReturn(Arrays.asList(null, 2L));
        when(boardRolePermissionsRepository.findRoleIdsByBoardIdAndPerm(1L, BoardRolePermissionType.POST)).thenReturn(Arrays.asList((Long) null));
        assertFalse(s.canViewBoard(1L, Set.of(3L)));
        assertFalse(s.canPostBoard(1L, Set.of(2L)));
    }
}
