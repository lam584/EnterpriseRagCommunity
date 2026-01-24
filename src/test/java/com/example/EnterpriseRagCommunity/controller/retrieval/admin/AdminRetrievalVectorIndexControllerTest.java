package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRetrievalVectorIndexControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostsRepository postsRepository;

    @Autowired
    VectorIndicesRepository vectorIndicesRepository;

    @MockBean
    AiEmbeddingService embeddingService;

    @MockBean
    RagPostsIndexService indexService;

    @MockBean
    ElasticsearchTemplate esTemplate;

    @Test
    void buildPosts_should_return_success_counts() throws Exception {
        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(1L);
        p.setAuthorId(1L);
        p.setTitle("t");
        p.setContent("hello world");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setPublishedAt(LocalDateTime.now());
        p.setIsDeleted(false);
        postsRepository.save(p);

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setProvider(VectorIndexProvider.OTHER);
        vi.setCollectionName("rag_post_chunks_test_v1_" + System.currentTimeMillis());
        vi.setMetric("cosine");
        vi.setDim(3);
        vi.setStatus(VectorIndexStatus.READY);
        vi = vectorIndicesRepository.save(vi);

        when(embeddingService.embedOnce(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "text-embedding-v4"));

        doNothing().when(indexService).ensureIndex(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt());
        when(esTemplate.save(ArgumentMatchers.any(Document.class), ArgumentMatchers.any(IndexCoordinates.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/admin/retrieval/vector-indices/{id}/build/posts", vi.getId())
                        .param("fromPostId", String.valueOf(p.getId()))
                        .with(user("admin@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_index:action")))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(1))
                .andExpect(jsonPath("$.totalChunks").value(1))
                .andExpect(jsonPath("$.successChunks").value(1))
                .andExpect(jsonPath("$.failedChunks").value(0));
    }
}
