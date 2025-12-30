package com.example.EnterpriseRagCommunity;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
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

    @Test
    void listPosts_sortByHotScore_shouldNot500() throws Exception {
        // Ensure there is at least one published post, so the endpoint has something to return.
        // We keep fields minimal; DB defaults handle createdAt/updatedAt.
        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(1L);
        p.setAuthorId(1L);
        p.setTitle("sort test");
        p.setContent("content");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setPublishedAt(LocalDateTime.now());
        p.setIsDeleted(false);
        postsRepository.save(p);

        mockMvc.perform(get("/api/posts")
                        .param("boardId", "1")
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

