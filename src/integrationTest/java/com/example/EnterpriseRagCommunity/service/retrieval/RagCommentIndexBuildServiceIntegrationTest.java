package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsBuildResponse;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexProvider;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.testsupport.MySqlTestcontainersBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RagCommentIndexBuildServiceIntegrationTest extends MySqlTestcontainersBase {

    private final RagCommentIndexBuildService buildService;
    private final VectorIndicesRepository vectorIndicesRepository;
    private final CommentsRepository commentsRepository;
    private final JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ElasticsearchTemplate esTemplate;

    @MockitoBean
    private RagCommentsIndexService ragCommentsIndexService;

    @MockitoBean
    private AiEmbeddingService aiEmbeddingService;

    @MockitoBean
    private SystemConfigurationService systemConfigurationService;

    @MockitoBean
    private LlmRoutingService llmRoutingService;

    RagCommentIndexBuildServiceIntegrationTest(
            RagCommentIndexBuildService buildService,
            VectorIndicesRepository vectorIndicesRepository,
            CommentsRepository commentsRepository,
            JdbcTemplate jdbcTemplate) {
        this.buildService = buildService;
        this.vectorIndicesRepository = vectorIndicesRepository;
        this.commentsRepository = commentsRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUpMocks() throws Exception {
        IndexOperations ops = Mockito.mock(IndexOperations.class);
        when(esTemplate.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        doNothing().when(ops).refresh();

        when(esTemplate.save(any(org.springframework.data.elasticsearch.core.document.Document.class), any(IndexCoordinates.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        doNothing().when(ragCommentsIndexService).ensureIndex(anyString(), anyInt());

        Mockito.doAnswer(inv -> new AiEmbeddingService.EmbeddingResult(
                        new float[]{0.1f, 0.2f, 0.3f, 0.4f},
                        4,
                        String.valueOf(inv.getArgument(1))
                ))
                .when(aiEmbeddingService)
                .embedOnceForTask(anyString(), anyString(), any(), any());

        when(systemConfigurationService.getConfig(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if ("spring.elasticsearch.uris".equals(k)) return "http://127.0.0.1:1";
            if ("APP_ES_API_KEY".equals(k)) return "";
            return null;
        });
    }

    @Test
    void buildComments_shouldReturnStats_andUpdateVectorIndexStatus_whenClearFalse() {
        long authorId = insertAndReturnId(
                "insert into users(tenant_id,email,username,password_hash,status,is_deleted,created_at,updated_at,access_version) " +
                        "values (null,?,?,?,?,?,?,?,0)",
                "it-user-" + UUID.randomUUID() + "@example.com",
                "it-user-" + UUID.randomUUID(),
                "x",
                "ACTIVE",
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        long boardId = insertAndReturnId(
                "insert into boards(tenant_id,parent_id,name,description,visible,sort_order,created_at,updated_at) " +
                        "values (null,null,?,?,?,?,?,?)",
                "it-board-" + UUID.randomUUID(),
                "it board",
                1,
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        long postId = insertAndReturnId(
                "insert into posts(tenant_id,board_id,author_id,title,content,content_format,content_length,is_chunked_review,status,is_deleted,created_at,updated_at) " +
                        "values (null,?,?,?,?,?,?,?,?,?,?,?)",
                boardId,
                authorId,
                "it post",
                "it post content",
                "MARKDOWN",
                0,
                0,
                "PUBLISHED",
                0,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        VectorIndicesEntity vi = new VectorIndicesEntity();
        vi.setProvider(VectorIndexProvider.OTHER);
        vi.setCollectionName("it_rag_comments_" + UUID.randomUUID().toString().replace("-", ""));
        vi.setMetric("cosine");
        vi.setDim(4);
        vi.setStatus(VectorIndexStatus.READY);
        vi.setMetadata(new LinkedHashMap<>());
        VectorIndicesEntity savedVi = vectorIndicesRepository.save(vi);

        CommentsEntity c = new CommentsEntity();
        c.setPostId(postId);
        c.setParentId(null);
        c.setAuthorId(authorId);
        c.setContent("hello integration test comment");
        c.setStatus(CommentStatus.VISIBLE);
        c.setIsDeleted(false);
        c.setMetadata(null);
        c.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        c.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        CommentsEntity savedC = commentsRepository.save(c);

        RagCommentsBuildResponse resp = buildService.buildComments(
                savedVi.getId(),
                null,
                50,
                200,
                0,
                false,
                "it-embedding-model",
                4
        );

        assertThat(resp.getTotalComments()).isEqualTo(1);
        assertThat(resp.getTotalChunks()).isEqualTo(1);
        assertThat(resp.getSuccessChunks()).isEqualTo(1);
        assertThat(resp.getFailedChunks()).isEqualTo(0);
        assertThat(resp.getEmbeddingModel()).isEqualTo("it-embedding-model");
        assertThat(resp.getEmbeddingDims()).isEqualTo(4);
        assertThat(resp.getLastCommentId()).isEqualTo(savedC.getId());
        assertThat(resp.getCleared()).isNull();

        VectorIndicesEntity updated = vectorIndicesRepository.findById(savedVi.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(VectorIndexStatus.READY);
        assertThat(updated.getDim()).isEqualTo(4);

        Map<String, Object> meta = updated.getMetadata();
        assertThat(meta).isNotNull();
        assertThat(meta.get("sourceType")).isEqualTo("COMMENT");
        assertThat(meta.get("esIndex")).isEqualTo(updated.getCollectionName());
        assertThat(numberLike(meta.get("lastBuildTotalComments"))).isEqualTo(1L);
        assertThat(numberLike(meta.get("lastBuildTotalChunks"))).isEqualTo(1L);
        assertThat(numberLike(meta.get("lastBuildSuccessChunks"))).isEqualTo(1L);
        assertThat(numberLike(meta.get("lastBuildFailedChunks"))).isEqualTo(0L);

        verify(ragCommentsIndexService).ensureIndex(eq(updated.getCollectionName()), eq(4));
    }

    private static long numberLike(Object v) {
        return switch (v) {
            case null -> 0L;
            case Number n -> n.longValue();
            case String s -> {
                String t = s.trim();
                yield t.isBlank() ? 0L : Long.parseLong(t);
            }
            default -> throw new IllegalArgumentException("not a number: " + v.getClass().getName());
        };
    }

    private static PreparedStatement prepareStatement(Connection con, String sql, Object... args) throws java.sql.SQLException {
        PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
        return ps;
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> prepareStatement(con, sql, args), keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("insert did not return generated key");
        return key.longValue();
    }
}
