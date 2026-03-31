package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.BoardsDTO;
import com.example.EnterpriseRagCommunity.service.BoardService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminBoardsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminBoardsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private BoardAccessControlService boardAccessControlService;

    @Test
    void createBoard_shouldEscapeHtmlFieldsInResponse() throws Exception {
        BoardsDTO dto = new BoardsDTO();
        dto.setId(9L);
        dto.setName("<svg onload=alert(1)>");
        dto.setDescription("<b>desc</b>");
        when(boardService.createBoard(any())).thenReturn(dto);

        mockMvc.perform(post("/api/admin/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"description\":\"y\",\"visible\":true,\"sortOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("&lt;svg onload=alert(1)&gt;"))
                .andExpect(jsonPath("$.description").value("&lt;b&gt;desc&lt;/b&gt;"));
    }
}

