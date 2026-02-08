package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AdminBoardsAccessControlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardsRepository boardsRepository;

    private BoardsEntity createBoard() {
        BoardsEntity b = new BoardsEntity();
        b.setTenantId(null);
        b.setParentId(null);
        b.setName("b-" + System.nanoTime());
        b.setDescription(null);
        b.setVisible(true);
        b.setSortOrder(0);
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        return boardsRepository.save(b);
    }

    @Test
    @WithMockUser(username = "admin", authorities = {"PERM_admin_boards:read", "PERM_admin_boards:write"})
    void accessControl_shouldKeepDtoShape_afterReplace() throws Exception {
        BoardsEntity b = createBoard();

        mockMvc.perform(put("/api/admin/boards/{id}/access-control", b.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "viewRoleIds": [1, 2],
                                  "postRoleIds": [3],
                                  "moderatorUserIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boardId").value(b.getId()))
                .andExpect(jsonPath("$.viewRoleIds").isArray())
                .andExpect(jsonPath("$.postRoleIds").isArray())
                .andExpect(jsonPath("$.moderatorUserIds").isArray());

        mockMvc.perform(get("/api/admin/boards/{id}/access-control", b.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boardId").value(b.getId()))
                .andExpect(jsonPath("$.viewRoleIds").isArray())
                .andExpect(jsonPath("$.postRoleIds").isArray())
                .andExpect(jsonPath("$.moderatorUserIds").isArray());
    }
}
