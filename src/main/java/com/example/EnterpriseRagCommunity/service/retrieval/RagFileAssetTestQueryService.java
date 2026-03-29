package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesTestQueryResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
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
public class RagFileAssetTestQueryService {

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagFileAssetsIndexService indexService;
    private final LlmGateway llmGateway;
    private final LlmRoutingService llmRoutingService;
    private final ObjectMapper objectMapper;
    private final SystemConfigurationService systemConfigurationService;

    public RagFilesTestQueryResponse testQuery(Long vectorIndexId, RagFilesTestQueryRequest req) {
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
                ? indexService.defaultIndexName()
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
        } else if (overrideProviderId != null && overrideModel == null) {
            providerToUse = overrideProviderId;
        }

        AiEmbeddingService.EmbeddingResult er;
        try {
            if (modelToUse == null) {
                LlmRoutingService.RouteTarget target = (providerToUse == null)
                        ? llmRoutingService.pickNext(LlmQueueTaskType.POST_EMBEDDING, new HashSet<>())
                        : llmRoutingService.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, providerToUse, new HashSet<>());
                if (target == null) {
                    throw new IllegalStateException(providerToUse == null
                            ? "no eligible embedding target (please check embedding routing config)"
                            : ("no eligible embedding target for providerId=" + providerToUse + " (please check embedding routing config)"));
                }
                er = llmGateway.embedOnceRouted(LlmQueueTaskType.POST_EMBEDDING, target.providerId(), target.modelName(), q);
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
            indexService.ensureIndex(indexName, dimsToUse, true);
        } catch (Exception e) {
            throw new IllegalStateException("Ensure ES index failed: " + e.getMessage(), e);
        }
        if (storedDims == null || storedDims <= 0) {
            vi.setDim(inferredDims);
            vectorIndicesRepository.save(vi);
        }

        String body = buildKnnSearchBody(topK, numCandidates, req.getFileAssetId(), req.getPostId(), vec);
        JsonNode root = postSearch(indexName, body);

        List<RagFilesTestQueryResponse.Hit> hits = new ArrayList<>();
        JsonNode arr = root.path("hits").path("hits");
        if (arr.isArray()) {
            for (JsonNode h : arr) {
                RagFilesTestQueryResponse.Hit out = new RagFilesTestQueryResponse.Hit();
                out.setDocId(h.path("_id").asText(null));
                if (h.hasNonNull("_score")) out.setScore(h.path("_score").asDouble());

                JsonNode src = h.path("_source");
                if (src.hasNonNull("file_asset_id")) out.setFileAssetId(src.path("file_asset_id").asLong());
                if (src.hasNonNull("chunk_index")) out.setChunkIndex(src.path("chunk_index").asInt());
                if (src.hasNonNull("owner_user_id")) out.setOwnerUserId(src.path("owner_user_id").asLong());
                out.setFileName(src.path("file_name").asText(null));
                out.setMimeType(src.path("mime_type").asText(null));

                JsonNode postIds = src.path("post_ids");
                if (postIds.isArray()) {
                    List<Long> ids = new ArrayList<>();
                    for (JsonNode x : postIds) {
                        if (x != null && x.isNumber()) ids.add(x.asLong());
                    }
                    out.setPostIds(ids.isEmpty() ? null : ids);
                }

                String text = src.path("content_text").asText(null);
                if (text != null && text.length() > 240) text = text.substring(0, 240) + "...";
                out.setContentTextPreview(text);

                hits.add(out);
            }
        }

        RagFilesTestQueryResponse resp = new RagFilesTestQueryResponse();
        resp.setIndexName(indexName);
        resp.setTopK(topK);
        resp.setFileAssetId(req.getFileAssetId());
        resp.setPostId(req.getPostId());
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(modelToUse);
        resp.setEmbeddingProviderId(providerToUse);
        resp.setNumCandidates(numCandidates);
        resp.setTookMs(System.currentTimeMillis() - started);
        resp.setHits(hits);
        return resp;
    }

    private JsonNode postSearch(String indexName, String body) {
        String elasticsearchUris = systemConfigurationService.getConfig("spring.elasticsearch.uris");
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

            String elasticsearchApiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey.trim());
            }

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

    private static String buildKnnSearchBody(int size, int numCandidates, Long fileAssetId, Long postId, float[] vec) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"size\":").append(size);
        sb.append(",\"query\":{\"bool\":{\"filter\":[");
        boolean first = true;
        if (fileAssetId != null) {
            sb.append("{\"term\":{\"file_asset_id\":").append(fileAssetId).append("}}");
            first = false;
        }
        if (postId != null) {
            if (!first) sb.append(',');
            sb.append("{\"term\":{\"post_ids\":").append(postId).append("}}");
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

    private static String toNonBlank(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }
}
