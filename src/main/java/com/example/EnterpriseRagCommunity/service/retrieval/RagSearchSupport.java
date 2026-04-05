package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class RagSearchSupport {

    private RagSearchSupport() {
    }

    public static int clampTopK(Integer topK) {
        int value = topK == null ? 8 : topK;
        return Math.max(1, Math.min(50, value));
    }

    public static void requireVectorIndexId(Long vectorIndexId) {
        if (vectorIndexId == null) {
            throw new IllegalArgumentException("vectorIndexId is required");
        }
    }

    public static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("req is required");
        }
    }

    public static PreparedTestQuery prepareTestQuery(
            Long vectorIndexId,
            Object request,
            String queryText,
            Integer topK,
            Integer numCandidates,
            String embeddingModel,
            VectorIndicesRepository vectorIndicesRepository,
            Supplier<String> defaultIndexNameSupplier
    ) {
        requireVectorIndexId(vectorIndexId);
        requireRequest(request);
        String requiredQueryText = requireQueryText(queryText);
        int resolvedTopK = clampTopK(topK);
        int resolvedNumCandidates = resolveNumCandidates(numCandidates, resolvedTopK);
        VectorIndicesEntity vectorIndex = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));
        String indexName = resolveIndexName(vectorIndex, defaultIndexNameSupplier);
        return new PreparedTestQuery(
                vectorIndex,
                requiredQueryText,
                resolvedTopK,
                resolvedNumCandidates,
                indexName,
                System.currentTimeMillis(),
                toNonBlank(embeddingModel)
        );
    }

    public static String requireQueryText(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText is required");
        }
        return queryText;
    }

    public static int resolveNumCandidates(Integer numCandidates, int topK) {
        if (numCandidates == null) {
            return Math.max(100, topK * 10);
        }
        return Math.max(10, Math.min(10_000, numCandidates));
    }

    public static String toNonBlank(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    public static String previewText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    public static float[] embedQuery(LlmGateway llmGateway, String embeddingModel, String queryText) {
        try {
            AiEmbeddingService.EmbeddingResult embeddingResult = llmGateway.embedOnceRouted(
                    LlmQueueTaskType.POST_EMBEDDING,
                    null,
                    embeddingModel,
                    queryText
            );
            return embeddingResult == null ? null : embeddingResult.vector();
        } catch (Exception ex) {
            throw new IllegalStateException("Embedding failed: " + ex.getMessage(), ex);
        }
    }

    public static List<Long> readLongList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isNumber()) {
                ids.add(item.asLong());
            }
        }
        return ids;
    }

    public static SearchHitEnvelope readSearchHit(JsonNode hitNode) {
        if (hitNode == null) {
            return new SearchHitEnvelope(null, null, null, null);
        }
        Double score = hitNode.hasNonNull("_score") ? hitNode.path("_score").asDouble() : null;
        return new SearchHitEnvelope(
                hitNode.path("_id").asText(null),
                score,
                hitNode.path("_source"),
                hitNode.path("highlight")
        );
    }

    public static String firstHighlightFragment(JsonNode highlightNode, String field) {
        if (highlightNode == null || field == null || field.isBlank()) {
            return null;
        }
        JsonNode arr = highlightNode.path(field);
        if (!arr.isArray() || arr.isEmpty()) {
            return null;
        }
        for (JsonNode node : arr) {
            if (node == null || !node.isTextual()) {
                continue;
            }
            String text = node.asText(null);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    public static void appendVector(StringBuilder sb, float[] vec) {
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
    }

    public record SearchHitEnvelope(
            String docId,
            Double score,
            JsonNode source,
            JsonNode highlight
    ) {
    }

    public static int resolveConfiguredDims(int configuredDims, int inferredDims, String mismatchPrefix) {
        if (configuredDims > 0 && configuredDims != inferredDims) {
            throw new IllegalStateException(mismatchPrefix + configuredDims + " but embedding length=" + inferredDims);
        }
        return configuredDims > 0 ? configuredDims : inferredDims;
    }

    public static int resolveStoredDims(Integer storedDims, int inferredDims, String mismatchPrefix) {
        Integer configuredDims = storedDims != null && storedDims > 0 ? storedDims : null;
        if (configuredDims != null && configuredDims != inferredDims) {
            throw new IllegalStateException(mismatchPrefix + configuredDims + ", embedding=" + inferredDims);
        }
        return configuredDims != null ? configuredDims : inferredDims;
    }

    public static int ensureIndexAndSyncDims(
            VectorIndicesEntity vectorIndex,
            float[] vec,
            IntConsumerWithException ensureIndexAction,
            Consumer<VectorIndicesEntity> persistAction
    ) {
        if (vec == null || vec.length == 0) {
            throw new IllegalStateException("embedding returned empty vector");
        }
        Integer storedDims = vectorIndex == null ? null : vectorIndex.getDim();
        int inferredDims = vec.length;
        int dimsToUse = resolveStoredDims(storedDims, inferredDims, "vector index dim mismatch: stored=");
        try {
            ensureIndexAction.accept(dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }
        if (vectorIndex != null && (storedDims == null || storedDims <= 0)) {
            vectorIndex.setDim(inferredDims);
            persistAction.accept(vectorIndex);
        }
        return dimsToUse;
    }

    public static EmbeddedQuery embedAndEnsureIndex(
            LlmGateway llmGateway,
            String embeddingModel,
            String queryText,
            int configuredDims,
            String indexName,
            IntConsumerWithException ensureIndexAction
    ) {
        float[] vec = embedQuery(llmGateway, embeddingModel, queryText);
        if (vec == null || vec.length == 0) {
            return new EmbeddedQuery(indexName, null, 0);
        }
        int dimsToUse = resolveConfiguredDims(configuredDims, vec.length, "Embedding dims mismatch: configured=");
        try {
            ensureIndexAction.accept(dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }
        return new EmbeddedQuery(indexName, vec, dimsToUse);
    }

    public static String resolveIndexName(VectorIndicesEntity vectorIndex, Supplier<String> defaultIndexNameSupplier) {
        String collectionName = vectorIndex == null ? null : vectorIndex.getCollectionName();
        return (collectionName == null || collectionName.isBlank())
                ? defaultIndexNameSupplier.get()
                : collectionName.trim();
    }

    public static Set<Long> publishedPostIds(PostsRepository postsRepository, Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> visible = new HashSet<>();
        postsRepository.findByIdInAndIsDeletedFalseAndStatus(new ArrayList<>(postIds), PostStatus.PUBLISHED)
                .forEach(post -> {
                    if (post != null && post.getId() != null) {
                        visible.add(post.getId());
                    }
                });
        return visible;
    }

    public static JsonNode postSearch(
            ObjectMapper objectMapper,
            SystemConfigurationService systemConfigurationService,
            String indexName,
            String body,
            String filterPath,
            boolean allowEmptyBody
    ) {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);
        try {
            URL url = java.net.URI.create(endpoint + "/" + indexName + "/_search?filter_path=" + filterPath).toURL();
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
            if (is == null) {
                if (allowEmptyBody) {
                    return objectMapper.createObjectNode();
                }
                throw new IllegalStateException("ES returned HTTP " + code + " without body");
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("ES error HTTP " + code + ": " + json);
            }
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("ES search failed: " + ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    public interface IntConsumerWithException {
        void accept(int value) throws Exception;
    }

    public record PreparedTestQuery(
            VectorIndicesEntity vectorIndex,
            String queryText,
            int topK,
            int numCandidates,
            String indexName,
            long startedAt,
            String overrideModel
    ) {
    }

    public record EmbeddedQuery(String indexName, float[] vector, int dimsToUse) {
    }
}
