package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagPostChatRetrievalService {

    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    private final AiEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String elasticsearchUris;

    @Value("${app.es.api-key:}")
    private String elasticsearchApiKey;

    public List<Hit> retrieve(String queryText, int topK, Long boardId) {
        if (queryText == null || queryText.isBlank()) return List.of();
        int k = Math.max(1, Math.min(20, topK));

        AiEmbeddingService.EmbeddingResult er;
        try {
            er = embeddingService.embedOnce(queryText, ragProps.getEs().getEmbeddingModel());
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }
        float[] vec = er == null ? null : er.vector();
        if (vec == null || vec.length == 0) return List.of();

        int configuredDims = ragProps.getEs().getEmbeddingDims();
        int inferredDims = vec.length;
        if (configuredDims > 0 && configuredDims != inferredDims) {
            throw new IllegalStateException("Embedding dims mismatch: configured=" + configuredDims + " but embedding length=" + inferredDims);
        }
        int dimsToUse = configuredDims > 0 ? configuredDims : inferredDims;

        String indexName = ragProps.getEs().getIndex();
        try {
            indexService.ensureIndex(indexName, dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }

        String body = buildKnnSearchBody(k, Math.max(100, k * 10), boardId, vec);
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
        return hits;
    }

    private JsonNode postSearch(String indexName, String body) {
        String endpoint = elasticsearchUris;
        if (endpoint == null || endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
        if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

        try {
            URL url = new URL(endpoint + "/" + indexName + "/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey.trim());
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return objectMapper.createObjectNode();
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) throw new IllegalStateException("ES error HTTP " + code + ": " + json);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("ES search failed: " + e.getMessage(), e);
        }
    }

    private static String buildKnnSearchBody(int size, int numCandidates, Long boardId, float[] vec) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"query\":{\"bool\":{\"filter\":[");
        if (boardId != null) {
            sb.append("{\"term\":{\"board_id\":").append(boardId).append("}}");
        }
        sb.append("]}}");
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vec[i]));
        }
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private Long postId;
        private Integer chunkIndex;
        private Long boardId;
        private String title;
        private String contentText;
    }
}
