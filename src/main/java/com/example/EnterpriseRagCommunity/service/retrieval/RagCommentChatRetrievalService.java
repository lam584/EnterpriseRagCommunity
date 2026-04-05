package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagCommentChatRetrievalService {

    private final RetrievalRagProperties ragProps;
    private final RagCommentsIndexService indexService;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final PostsRepository postsRepository;
    private final SystemConfigurationService systemConfigurationService;

    public List<Hit> retrieve(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) return List.of();
        int k = RagSearchSupport.clampTopK(topK);
        float[] vec = RagSearchSupport.embedQuery(llmGateway, ragProps.getEs().getEmbeddingModel(), queryText);
        if (vec == null || vec.length == 0) return List.of();
        int dimsToUse = RagSearchSupport.resolveConfiguredDims(
                ragProps.getEs().getEmbeddingDims(),
                vec.length,
                "Embedding dims mismatch: configured="
        );

        String indexName = indexService.defaultIndexName();
        try {
            indexService.ensureIndex(indexName, dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }

        String body = buildKnnSearchBody(k, RagSearchSupport.resolveNumCandidates(null, k), vec, queryText);
        JsonNode root = postSearch(indexName, body);

        List<Hit> hits = new ArrayList<>();
        JsonNode arr = root.path("hits").path("hits");
        if (arr.isArray()) {
            for (JsonNode h : arr) {
                RagSearchSupport.SearchHitEnvelope envelope = RagSearchSupport.readSearchHit(h);
                Hit out = new Hit();
                out.setDocId(envelope.docId());
                out.setScore(envelope.score());
                JsonNode src = envelope.source();
                JsonNode hl = envelope.highlight();
                if (src.hasNonNull("comment_id")) out.setCommentId(src.path("comment_id").asLong());
                if (src.hasNonNull("post_id")) out.setPostId(src.path("post_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                out.setContentText(src.path("content_text").asText(null));
                out.setContentHighlight(RagSearchSupport.firstHighlightFragment(hl, "content_text"));
                hits.add(out);
            }
        }
        return filterVisiblePosts(hits);
    }

    private List<Hit> filterVisiblePosts(List<Hit> hits) {
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
                "hits.hits._id,hits.hits._score,hits.hits._source,hits.hits.highlight",
                true
        );
    }

    private static String buildKnnSearchBody(int size, int numCandidates, float[] vec) {
        return buildKnnSearchBody(size, numCandidates, vec, null);
    }

    private static String buildKnnSearchBody(int size, int numCandidates, float[] vec, String queryText) {
        return RagSearchJsonSupport.buildKnnSearchBody(
                size,
                numCandidates,
                vec,
                queryText,
                "\"content_text\":{\"number_of_fragments\":1,\"fragment_size\":220}"
        );
    }
    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private Long commentId;
        private Long postId;
        private Integer chunkIndex;
        private String contentText;
        private String contentHighlight;
    }
}
