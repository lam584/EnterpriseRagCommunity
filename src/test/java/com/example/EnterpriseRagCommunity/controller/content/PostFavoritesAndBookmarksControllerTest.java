package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PostFavoritesAndBookmarksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private BoardsRepository boardsRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private ReactionsRepository reactionsRepository;

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

    private PostsEntity createPost(Long boardId, Long authorId) {
        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(boardId);
        p.setAuthorId(authorId);
        p.setTitle("t-" + System.nanoTime());
        p.setContent("c");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setPublishedAt(LocalDateTime.now());
        p.setIsDeleted(false);
        return postsRepository.save(p);
    }

    @Test
    @WithMockUser(username = "u", authorities = {})
    void favorite_and_bookmarks_shouldWork_withoutFavoritesTable() throws Exception {
        UsersEntity u = ensureUser("u");
        reactionsRepository.deleteByUserIdAndTargetTypeAndType(u.getId(), ReactionTargetType.POST, ReactionType.FAVORITE);
        BoardsEntity b = createBoard();
        PostsEntity p = createPost(b.getId(), u.getId());

        mockMvc.perform(post("/api/posts/{postId}/favorite", p.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoritedByMe").value(true))
                .andExpect(jsonPath("$.favoriteCount").value(1));

        mockMvc.perform(get("/api/posts/bookmarks?page=1&pageSize=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(p.getId()))
                .andExpect(jsonPath("$.content[0].favoritedByMe").value(true))
                .andExpect(jsonPath("$.content[0].favoriteCount").value(1));

        mockMvc.perform(delete("/api/posts/{postId}/favorite", p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoritedByMe").value(false))
                .andExpect(jsonPath("$.favoriteCount").value(0));

        mockMvc.perform(get("/api/posts/bookmarks?page=1&pageSize=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
