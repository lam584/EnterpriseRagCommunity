package com.example.EnterpriseRagCommunity.controller.moderation;

import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModeratorBoardsControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMyBoards_authNull_throwsAccessDeniedException() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(AccessDeniedException.class, controller::listMyBoards);
        verifyNoInteractions(boardModeratorsRepository, boardsRepository, administratorService);
    }

    @Test
    void listMyBoards_notAuthenticated_throwsAccessDeniedException() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(AccessDeniedException.class, controller::listMyBoards);
        verifyNoInteractions(boardModeratorsRepository, boardsRepository, administratorService);
    }

    @Test
    void listMyBoards_anonymousPrincipal_throwsAccessDeniedException() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(AccessDeniedException.class, controller::listMyBoards);
        verifyNoInteractions(boardModeratorsRepository, boardsRepository, administratorService);
    }

    @Test
    void listMyBoards_userNotFound_throwsAccessDeniedException() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(AccessDeniedException.class, controller::listMyBoards);
        verifyNoInteractions(boardModeratorsRepository, boardsRepository);
    }

    @Test
    void listMyBoards_boardIdsNull_returnsEmptyList() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UsersEntity user = new UsersEntity();
        user.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(user));
        when(boardModeratorsRepository.findBoardIdsByUserId(10L)).thenReturn(null);

        List<BoardsDTO> result = controller.listMyBoards();

        Assertions.assertTrue(result.isEmpty());
        verify(boardModeratorsRepository).findBoardIdsByUserId(10L);
        verifyNoInteractions(boardsRepository);
    }

    @Test
    void listMyBoards_boardIdsEmpty_returnsEmptyList() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UsersEntity user = new UsersEntity();
        user.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(user));
        when(boardModeratorsRepository.findBoardIdsByUserId(10L)).thenReturn(List.of());

        List<BoardsDTO> result = controller.listMyBoards();

        Assertions.assertTrue(result.isEmpty());
        verify(boardModeratorsRepository).findBoardIdsByUserId(10L);
        verifyNoInteractions(boardsRepository);
    }

    @Test
    void listMyBoards_filtersNullEntitiesAndKeepsDistinctOrder() {
        BoardModeratorsRepository boardModeratorsRepository = mock(BoardModeratorsRepository.class);
        BoardsRepository boardsRepository = mock(BoardsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        ModeratorBoardsController controller =
                new ModeratorBoardsController(boardModeratorsRepository, boardsRepository, administratorService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UsersEntity user = new UsersEntity();
        user.setId(10L);
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(user));
        when(boardModeratorsRepository.findBoardIdsByUserId(10L)).thenReturn(List.of(1L, 1L, 2L, 3L));

        BoardsEntity valid1 = new BoardsEntity();
        valid1.setId(1L);
        valid1.setName("Board A");
        BoardsEntity invalidNoId = new BoardsEntity();
        invalidNoId.setName("Invalid");
        BoardsEntity valid2 = new BoardsEntity();
        valid2.setId(2L);
        valid2.setName("Board B");
        when(boardsRepository.findAllById(any())).thenReturn(Arrays.asList(null, invalidNoId, valid1, valid2));

        List<BoardsDTO> result = controller.listMyBoards();

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(1L, result.get(0).getId());
        Assertions.assertEquals("Board A", result.get(0).getName());
        Assertions.assertEquals(2L, result.get(1).getId());
        Assertions.assertEquals("Board B", result.get(1).getName());

        ArgumentCaptor<Iterable<Long>> idsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(boardsRepository).findAllById(idsCaptor.capture());
        Assertions.assertIterableEquals(List.of(1L, 2L, 3L), idsCaptor.getValue());
        verify(boardModeratorsRepository).findBoardIdsByUserId(eq(10L));
    }
}
