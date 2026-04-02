package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagFileAssetChatRetrievalService {

    private final RagFileAssetsIndexService indexService;
    private final RetrievalRagProperties ragProps;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final PostsRepository postsRepository;
    private final SystemConfigurationService systemConfigurationService;

    public List<Hit> retrieve(String queryText, int topK) {
        if (queryText == null || queryText.isBlank()) return List.of();
        int k = Math.max(1, Math.min(50, topK));

        AiEmbeddingService.EmbeddingResult er;
        try {
            String mo = ragProps.getEs().getEmbeddingModel();
            er = llmGateway.embedOnceRouted(LlmQueueTaskType.POST_EMBEDDING, null, mo, queryText);
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
        try {
            indexService.ensureIndex(indexService.defaultIndexName(), dimsToUse, true);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }

        String body = buildKnnSearchBody(k, Math.max(100, k * 10), vec, queryText);
        JsonNode root = postSearch(indexService.defaultIndexName(), body);

        List<Hit> hits = new ArrayList<>();
        JsonNode arr = root.path("hits").path("hits");
        if (arr.isArray()) {
            for (JsonNode h : arr) {
                Hit out = new Hit();
                out.setDocId(h.path("_id").asText(null));
                if (h.hasNonNull("_score")) out.setScore(h.path("_score").asDouble());
                JsonNode src = h.path("_source");
                JsonNode hl = h.path("highlight");
                if (src.hasNonNull("file_asset_id")) out.setFileAssetId(src.path("file_asset_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                out.setFileName(src.path("file_name").asText(null));
                out.setMimeType(src.path("mime_type").asText(null));
                out.setContentText(src.path("content_text").asText(null));
                out.setContentHighlight(firstHighlightFragment(hl, "content_text"));
                JsonNode postIds = src.path("post_ids");
                if (postIds.isArray()) {
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode x : postIds) {
                        if (x != null && x.isNumber()) ids.add(x.asLong());
                    }
                    out.setPostIds(ids.isEmpty() ? null : ids);
                }
                hits.add(out);
            }
        }
        return filterVisiblePosts(hits);
    }

    private List<Hit> filterVisiblePosts(List<Hit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        Set<Long> postIds = new LinkedHashSet<>();
        for (Hit h : hits) {
            if (h == null || h.getPostIds() == null) continue;
            for (Long pid : h.getPostIds()) {
                if (pid != null) postIds.add(pid);
            }
        }
        if (postIds.isEmpty()) return List.of();

        List<Long> ids = new ArrayList<>(postIds);
        Set<Long> ok = new HashSet<>();
        postsRepository.findByIdInAndIsDeletedFalseAndStatus(ids, PostStatus.PUBLISHED)
                .forEach(p -> {
                    if (p != null && p.getId() != null) ok.add(p.getId());
                });

        List<Hit> out = new ArrayList<>();
        for (Hit h : hits) {
            if (h == null || h.getPostIds() == null || h.getPostIds().isEmpty()) continue;
            boolean keep = false;
            for (Long pid : h.getPostIds()) {
                if (pid != null && ok.contains(pid)) {
                    keep = true;
                    break;
                }
            }
            if (keep) out.add(h);
        }
        return out;
    }

    private JsonNode postSearch(String indexName, String body) {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            URL url = java.net.URI.create(endpoint + "/" + indexName + "/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source,hits.hits.highlight").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(10_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

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

    private static String buildKnnSearchBody(int size, int numCandidates, float[] vec) {
        return buildKnnSearchBody(size, numCandidates, vec, null);
    }

    private static String buildKnnSearchBody(int size, int numCandidates, float[] vec, String queryText) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"knn\":{");
        sb.append("\"field\":\"embedding\"");
        sb.append(",\"query_vector\":[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        appendHighlightClause(sb, queryText);
        sb.append('}');
        return sb.toString();
    }

    private static void appendHighlightClause(StringBuilder sb, String queryText) {
        sb.append(",\"highlight\":{");
        sb.append("\"pre_tags\":[\"<em>\"],\"post_tags\":[\"</em>\"],");
        sb.append("\"fields\":{");
        sb.append("\"content_text\":{\"number_of_fragments\":1,\"fragment_size\":220},");
        sb.append("\"file_name\":{\"number_of_fragments\":1,\"fragment_size\":120}");
        sb.append('}');
        appendHighlightQuery(sb, queryText);
        sb.append('}');
    }

    private static void appendHighlightQuery(StringBuilder sb, String queryText) {
        String q = queryText == null ? "" : queryText.trim();
        if (q.isBlank()) return;
        sb.append(",\"highlight_query\":{");
        sb.append("\"simple_query_string\":{");
        sb.append("\"query\":\"").append(escapeJson(q)).append("\",");
        sb.append("\"fields\":[\"file_name^2\",\"content_text\"],\"default_operator\":\"or\"}}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String firstHighlightFragment(JsonNode highlightNode, String field) {
        if (highlightNode == null || field == null || field.isBlank()) return null;
        JsonNode arr = highlightNode.path(field);
        if (!arr.isArray() || arr.isEmpty()) return null;
        for (JsonNode x : arr) {
            if (x == null || !x.isTextual()) continue;
            String t = x.asText(null);
            if (t != null && !t.isBlank()) return t;
        }
        return null;
    }

    @Data
    public static class Hit {
        private String docId;
        private Double score;
        private Long fileAssetId;
        private Integer chunkIndex;
        private String fileName;
        private String mimeType;
        private List<Long> postIds;
        private String contentText;
        private String contentHighlight;
    }
}
