package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostsSortByWhitelistTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostsRepository postsRepository;

    @Autowired
    BoardsRepository boardsRepository;

    @Autowired
    UsersRepository usersRepository;

    @Test
    void listPosts_sortByHotScore_shouldNot500() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setTenantId(null);
        u.setEmail("u1@example.com");
        u.setUsername("u1");
        u.setPasswordHash("x");
        u.setStatus(AccountStatus.ACTIVE);
        u = usersRepository.save(u);

        LocalDateTime now = LocalDateTime.now();
        BoardsEntity b = new BoardsEntity();
        b.setTenantId(null);
        b.setParentId(null);
        b.setName("b1");
        b.setDescription(null);
        b.setVisible(true);
        b.setSortOrder(0);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        b = boardsRepository.save(b);

        // Ensure there is at least one published post, so the endpoint has something to return.
        // We keep fields minimal; DB defaults handle createdAt/updatedAt.
        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(b.getId());
        p.setAuthorId(u.getId());
        p.setTitle("sort test");
        p.setContent("content");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setPublishedAt(now);
        p.setIsDeleted(false);
        postsRepository.save(p);

        mockMvc.perform(get("/api/posts")
                        .param("boardId", String.valueOf(b.getId()))
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("status", "PUBLISHED")
                        .param("sortBy", "hotScore")
                        .param("sortOrderDirection", "DESC")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }
}

