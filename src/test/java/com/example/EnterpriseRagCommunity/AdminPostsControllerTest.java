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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminPostsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostsRepository postsRepository;

    @Test
    void adminPosts_allShouldIncludePending() throws Exception {
        // Arrange: insert one PENDING post
        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(1L);
        p.setAuthorId(1L);
        p.setTitle("pending-title");
        p.setContent("pending-content");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PENDING);
        p.setPublishedAt(null);
        p.setIsDeleted(false);
        postsRepository.save(p);

        // Act
        String body = mockMvc.perform(get("/api/admin/posts")
                        .param("status", "ALL")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert: response should contain PENDING
        // We keep assertion simple to avoid coupling to DTO mapping.
        assertThat(body).contains("\"status\":\"PENDING\"");
    }
}

