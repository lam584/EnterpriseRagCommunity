package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsTestQueryResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagPostTestQueryService {

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    private final LlmGateway llmGateway;
    private final LlmRoutingService llmRoutingService;
    private final ObjectMapper objectMapper;
    private final SystemConfigurationService systemConfigurationService;

    public RagPostsTestQueryResponse testQuery(Long vectorIndexId, RagPostsTestQueryRequest req) {
        if (vectorIndexId == null) throw new IllegalArgumentException("vectorIndexId is required");
        if (req == null) throw new IllegalArgumentException("req is required");
        String q = req.getQueryText();
        if (q == null || q.isBlank()) throw new IllegalArgumentException("queryText is required");

        int topK = req.getTopK() == null ? 8 : Math.max(1, Math.min(50, req.getTopK()));
        Integer numCandidates = req.getNumCandidates();
        if (numCandidates == null) {
            numCandidates = Math.max(100, topK * 10);
        } else {
            numCandidates = Math.max(10, Math.min(10_000, numCandidates));
        }

        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? ragProps.getEs().getIndex()
                : vi.getCollectionName().trim();

        long started = System.currentTimeMillis();

        String overrideModel = toNonBlank(req.getEmbeddingModel());
        String overrideProviderId = toNonBlank(req.getEmbeddingProviderId());
        boolean hasOverride = overrideModel != null && overrideProviderId != null;

        String fixedProviderId = toNonBlank(vi.getMetadata() == null ? null : vi.getMetadata().get("embeddingProviderId"));

        String modelToUse = null;
        String providerToUse = null;
        if (hasOverride) {
            modelToUse = overrideModel;
            providerToUse = overrideProviderId;
        } else if (fixedProviderId != null) {
            providerToUse = fixedProviderId;
        } else if (overrideProviderId != null) {
            providerToUse = overrideProviderId;
        }

        AiEmbeddingService.EmbeddingResult er;
        try {
            if (modelToUse == null) {
                LlmRoutingService.RouteTarget target = (providerToUse == null)
                        ? llmRoutingService.pickNext(LlmQueueTaskType.POST_EMBEDDING, new HashSet<>())
                        : llmRoutingService.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, providerToUse, new HashSet<>());
                if (target == null) {
                    String legacy = toNonBlank(ragProps.getEs().getEmbeddingModel());
                    if (legacy == null) {
                        throw new IllegalStateException(providerToUse == null
                                ? "no eligible embedding target (please check embedding routing config)"
                                : ("no eligible embedding target for providerId=" + providerToUse + " (please check embedding routing config)"));
                    }
                    er = llmGateway.embedOnceRouted(LlmQueueTaskType.POST_EMBEDDING, null, legacy, q);
                } else {
                    er = llmGateway.embedOnceRouted(LlmQueueTaskType.POST_EMBEDDING, target.providerId(), target.modelName(), q);
                }
            } else {
                er = llmGateway.embedOnceRouted(LlmQueueTaskType.POST_EMBEDDING, providerToUse, modelToUse, q);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }
        float[] vec = er == null ? null : er.vector();
        if (vec == null || vec.length == 0) throw new IllegalStateException("embedding returned empty vector");

        Integer storedDims = vi.getDim();
        Integer configuredDims = storedDims != null && storedDims > 0 ? storedDims : null;
        int inferredDims = vec.length;
        if (configuredDims != null && configuredDims != inferredDims) {
            throw new IllegalStateException("vector index dim mismatch: stored=" + configuredDims + ", embedding=" + inferredDims);
        }
        int dimsToUse = configuredDims != null ? configuredDims : inferredDims;
        try {
            indexService.ensureIndex(indexName, dimsToUse);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }
        if (storedDims == null || storedDims <= 0) {
            vi.setDim(inferredDims);
            vectorIndicesRepository.save(vi);
        }

        String body = buildKnnSearchBody(topK, numCandidates, req.getBoardId(), vec);
        JsonNode root = postSearch(indexName, body);

        List<RagPostsTestQueryResponse.Hit> hits = new ArrayList<>();
        JsonNode arr = root.path("hits").path("hits");
        if (arr.isArray()) {
            for (JsonNode h : arr) {
                RagPostsTestQueryResponse.Hit out = new RagPostsTestQueryResponse.Hit();
                out.setDocId(h.path("_id").asText(null));
                if (h.hasNonNull("_score")) out.setScore(h.path("_score").asDouble());

                JsonNode src = h.path("_source");
                if (src.hasNonNull("post_id")) out.setPostId(src.path("post_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                if (src.hasNonNull("author_id")) out.setAuthorId(src.path("author_id").asLong());
                if (src.hasNonNull("board_id")) out.setBoardId(src.path("board_id").asLong());
                out.setTitle(src.path("title").asText(null));

                String text = src.path("content_text").asText(null);
                if (text != null && text.length() > 240) text = text.substring(0, 240) + "...";
                out.setContentTextPreview(text);

                hits.add(out);
            }
        }

        RagPostsTestQueryResponse resp = new RagPostsTestQueryResponse();
        resp.setIndexName(indexName);
        resp.setTopK(topK);
        resp.setBoardId(req.getBoardId());
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(modelToUse);
        resp.setEmbeddingProviderId(providerToUse);
        resp.setNumCandidates(numCandidates);
        resp.setTookMs(System.currentTimeMillis() - started);
        resp.setHits(hits);
        return resp;
    }

    private JsonNode postSearch(String indexName, String body) {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            URL url = java.net.URI.create(endpoint + "/" + indexName + "/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source").toURL();
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
            if (is == null) throw new IllegalStateException("ES returned HTTP " + code + " without body");
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
            sb.append(vec[i]);
        }
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private static String toNonBlank(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }
}
