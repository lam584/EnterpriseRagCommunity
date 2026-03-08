package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.entity.content.BoardsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
class AdminRetrievalVectorIndexControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PostsRepository postsRepository;

    @Autowired
    BoardsRepository boardsRepository;

    @Autowired
    UsersRepository usersRepository;

    @Autowired
    VectorIndicesRepository vectorIndicesRepository;

    @MockitoBean
    AiEmbeddingService embeddingService;

    @MockitoBean
    RagPostsIndexService indexService;

    @MockitoBean
    ElasticsearchTemplate esTemplate;

    @MockitoBean
    SystemConfigurationService systemConfigurationService;

    private String createdIndexName;

    @BeforeEach
    void setUp() {
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("test-key");
        IndexOperations indexOperations = Mockito.mock(IndexOperations.class);
        when(esTemplate.indexOps(ArgumentMatchers.any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.delete()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (createdIndexName != null) {
            try {
                esTemplate.indexOps(IndexCoordinates.of(createdIndexName)).delete();
            } catch (Exception ignored) {
            } finally {
                createdIndexName = null;
            }
        }
    }

    @Test
    void buildPosts_should_return_success_counts() throws Exception {
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

        PostsEntity p = new PostsEntity();
        p.setTenantId(null);
        p.setBoardId(b.getId());
        p.setAuthorId(u.getId());
        p.setTitle("t");
        p.setContent("hello world");
        p.setContentFormat(ContentFormat.MARKDOWN);
        p.setStatus(PostStatus.PUBLISHED);
        p.setPublishedAt(now);
        p.setIsDeleted(false);
        p = postsRepository.save(p);

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setProvider(VectorIndexProvider.OTHER);
        vi.setCollectionName("rag_post_chunks_test_v1_" + System.currentTimeMillis());
        vi.setMetric("cosine");
        vi.setDim(3);
        vi.setStatus(VectorIndexStatus.READY);
        vi = vectorIndicesRepository.save(vi);
        createdIndexName = vi.getCollectionName();

        when(embeddingService.embedOnceForTask(
                ArgumentMatchers.<String>any(),
                ArgumentMatchers.<String>any(),
                ArgumentMatchers.<String>any(),
                ArgumentMatchers.any()
        ))
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
