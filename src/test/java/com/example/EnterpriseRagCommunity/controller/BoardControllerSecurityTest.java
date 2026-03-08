package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.service.BoardService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
class BoardControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BoardService boardService;

    @MockitoBean
    BoardAccessControlService boardAccessControlService;

    @Test
    void createBoard_shouldDeny_withoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/boards")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_boards:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"visible\":true,\"sortOrder\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createBoard_shouldAllow_withWritePermission() throws Exception {
        BoardsDTO dto = new BoardsDTO();
        dto.setId(1L);
        dto.setName("n");
        Mockito.when(boardService.createBoard(Mockito.any())).thenReturn(dto);

        mockMvc.perform(post("/api/boards")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_boards:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"visible\":true,\"sortOrder\":0}"))
                .andExpect(status().isCreated());
    }
}

