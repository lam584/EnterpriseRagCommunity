package com.example.EnterpriseRagCommunity.service.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagPostChatRetrievalService {

    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final PostsRepository postsRepository;
    private final SystemConfigurationService systemConfigurationService;

    public List<Hit> retrieve(String queryText, int topK, Long boardId) {
        if (queryText == null || queryText.isBlank()) return List.of();
        int k = Math.max(1, Math.min(20, topK));
        float[] vec = RagSearchSupport.embedQuery(llmGateway, ragProps.getEs().getEmbeddingModel(), queryText);
        if (vec == null || vec.length == 0) return List.of();
        int dimsToUse = RagSearchSupport.resolveConfiguredDims(
                ragProps.getEs().getEmbeddingDims(),
                vec.length,
                "Embedding dims mismatch: configured="
        );

        String indexName = ragProps.getEs().getIndex();
        try {
            indexService.ensureIndex(indexName, dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }

        String body = buildKnnSearchBody(k, RagSearchSupport.resolveNumCandidates(null, k), boardId, vec);
        JsonNode root = postSearch(indexName, body);

        List<Hit> hits = new ArrayList<>();
        JsonNode arr = root.path("hits").path("hits");
        if (arr.isArray()) {
            for (JsonNode h : arr) {
                Hit out = new Hit();
                out.setDocId(h.path("_id").asText(null));
                if (h.hasNonNull("_score")) out.setScore(h.path("_score").asDouble());
                JsonNode src = h.path("_source");
                if (src.hasNonNull("post_id")) out.setPostId(src.path("post_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                if (src.hasNonNull("board_id")) out.setBoardId(src.path("board_id").asLong());
                out.setTitle(src.path("title").asText(null));
                out.setContentText(src.path("content_text").asText(null));
                hits.add(out);
            }
        }
        return filterVisibleHits(hits);
    }

    private List<Hit> filterVisibleHits(List<Hit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        Set<Long> postIds = new LinkedHashSet<>();
        for (Hit h : hits) {
            if (h == null || h.getPostId() == null) continue;
            postIds.add(h.getPostId());
        }
        if (postIds.isEmpty()) return hits;

        Set<Long> ok = RagSearchSupport.publishedPostIds(postsRepository, postIds);

        List<Hit> out = new ArrayList<>();
        for (Hit h : hits) {
            if (h == null || h.getPostId() == null) continue;
            if (ok.contains(h.getPostId())) out.add(h);
        }
        return out;
    }

    private JsonNode postSearch(String indexName, String body) {
        return RagSearchSupport.postSearch(
                objectMapper,
                systemConfigurationService,
                indexName,
                body,
                "hits.hits._id,hits.hits._score,hits.hits._source",
                true
        );
    }

    private static String buildKnnSearchBody(int size, int numCandidates, Long boardId, float[] vec) {
        return RagPostSearchJsonSupport.buildKnnSearchBody(size, numCandidates, boardId, vec);
    }

    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private RetrievalHitType type;
        private String sourceType;
        private Long fileAssetId;
        private Long commentId;
        private Long postId;
        private Integer chunkIndex;
        private Long boardId;
        private String title;
        private String contentText;
    }
}
