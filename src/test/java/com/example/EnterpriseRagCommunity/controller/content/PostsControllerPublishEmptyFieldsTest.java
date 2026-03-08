package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PostsControllerPublishEmptyFieldsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BoardsRepository boardsRepository;

    private UsersEntity ensureUser(String email) {
        return usersRepository.findByEmailAndIsDeletedFalse(email).orElseGet(() -> {
            UsersEntity u = new UsersEntity();
            u.setEmail(email);
            u.setUsername(email);
            u.setPasswordHash("x");
            u.setStatus(AccountStatus.ACTIVE);
            u.setIsDeleted(false);
            return usersRepository.save(u);
        });
    }

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
    @WithMockUser(username = "u", authorities = {})
    void publish_shouldWork_whenTitleBlank_andTagsMissing() throws Exception {
        ensureUser("u");
        BoardsEntity b = createBoard();

        String body = """
                {
                  "boardId": %d,
                  "title": "   ",
                  "content": "hello"
                }
                """.formatted(b.getId());

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.boardId").value(b.getId()))
                .andExpect(jsonPath("$.title").value(""))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

