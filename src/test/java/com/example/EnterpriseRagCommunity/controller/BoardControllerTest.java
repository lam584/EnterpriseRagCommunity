package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.dto.content.BoardsQueryDTO;
import com.example.EnterpriseRagCommunity.service.BoardService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BoardController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private BoardAccessControlService boardAccessControlService;

    @Test
    void queryBoards_shouldDefaultVisibleTrue_andFilterInaccessibleBoards() throws Exception {
        BoardsDTO ok = new BoardsDTO();
        ok.setId(1L);
        BoardsDTO noId = new BoardsDTO();
        noId.setId(null);
        BoardsDTO denied = new BoardsDTO();
        denied.setId(2L);

        when(boardService.queryBoards(any())).thenReturn(new PageImpl<>(
                Arrays.asList(null, noId, ok, denied),
                PageRequest.of(0, 20),
                4
        ));
        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(10L));
        when(boardAccessControlService.canViewBoard(eq(1L), any())).thenReturn(true);
        when(boardAccessControlService.canViewBoard(eq(2L), any())).thenReturn(false);

        ArgumentCaptor<BoardsQueryDTO> captor = ArgumentCaptor.forClass(BoardsQueryDTO.class);
        when(boardService.queryBoards(captor.capture())).thenReturn(new PageImpl<>(
                Arrays.asList(null, noId, ok, denied),
                PageRequest.of(0, 20),
                4
        ));

        mockMvc.perform(get("/api/boards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1));

        BoardsQueryDTO sent = captor.getValue();
        assertThat(sent.getVisible()).isEqualTo(true);
    }

    @Test
    void queryBoards_shouldNotOverrideVisible_whenProvided() throws Exception {
        ArgumentCaptor<BoardsQueryDTO> captor = ArgumentCaptor.forClass(BoardsQueryDTO.class);
        when(boardService.queryBoards(captor.capture())).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        ));
        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of());

        mockMvc.perform(get("/api/boards").param("visible", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        BoardsQueryDTO sent = captor.getValue();
        assertThat(sent.getVisible()).isEqualTo(false);
    }
}

