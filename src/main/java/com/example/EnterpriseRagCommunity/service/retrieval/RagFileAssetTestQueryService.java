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
        RagSearchSupport.requireRequest(req);
        RagSearchSupport.PreparedTestQuery prepared = RagSearchSupport.prepareTestQuery(
                vectorIndexId,
                req,
                req.getQueryText(),
                req.getTopK(),
                req.getNumCandidates(),
                req.getEmbeddingModel(),
                vectorIndicesRepository,
                indexService::defaultIndexName
        );
        VectorIndicesEntity vi = prepared.vectorIndex();
        String q = prepared.queryText();
        int topK = prepared.topK();
        int numCandidates = prepared.numCandidates();
        String indexName = prepared.indexName();
        long started = prepared.startedAt();
        String overrideModel = prepared.overrideModel();
        String overrideProviderId = RagSearchSupport.toNonBlank(req.getEmbeddingProviderId());
        boolean hasOverride = overrideModel != null && overrideProviderId != null;

        String fixedProviderId = RagSearchSupport.toNonBlank(vi.getMetadata() == null ? null : vi.getMetadata().get("embeddingProviderId"));

        RagEmbeddingBuildSupport.ResolvedEmbeddingTarget embeddingTarget =
                RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                        llmRoutingService,
                        overrideModel,
                        overrideProviderId,
                        fixedProviderId,
                        false,
                        null
                );
        String modelToUse = embeddingTarget.model();
        String providerToUse = embeddingTarget.providerId();
        String responseEmbeddingModel = hasOverride ? overrideModel : null;
        String responseEmbeddingProviderId = overrideProviderId != null ? overrideProviderId : fixedProviderId;

        AiEmbeddingService.EmbeddingResult er;
        try {
            er = llmGateway.embedOnceRouted(LlmQueueTaskType.POST_EMBEDDING, providerToUse, modelToUse, q);
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }
        float[] vec = er == null ? null : er.vector();
        int dimsToUse = RagSearchSupport.ensureIndexAndSyncDims(
                vi,
                vec,
                dims -> indexService.ensureIndex(indexName, dims, true),
                vectorIndicesRepository::save
        );

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

                List<Long> ids = RagSearchSupport.readLongList(src.path("post_ids"));
                out.setPostIds(ids.isEmpty() ? null : ids);
                out.setContentTextPreview(RagSearchSupport.previewText(src.path("content_text").asText(null), 240));

                hits.add(out);
            }
        }

        RagFilesTestQueryResponse resp = new RagFilesTestQueryResponse();
        resp.setIndexName(indexName);
        resp.setTopK(topK);
        resp.setFileAssetId(req.getFileAssetId());
        resp.setPostId(req.getPostId());
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(responseEmbeddingModel);
        resp.setEmbeddingProviderId(responseEmbeddingProviderId);
        resp.setNumCandidates(numCandidates);
        resp.setTookMs(System.currentTimeMillis() - started);
        resp.setHits(hits);
        return resp;
    }

    private JsonNode postSearch(String indexName, String body) {
        return RagSearchSupport.postSearch(
                objectMapper,
                systemConfigurationService,
                indexName,
                body,
                "hits.hits._id,hits.hits._score,hits.hits._source",
                false
        );
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
        RagSearchSupport.appendVector(sb, vec);
        sb.append(']');
        sb.append(",\"k\":").append(size);
        sb.append(",\"num_candidates\":").append(numCandidates);
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }

    private static String toNonBlank(Object v) {
        return RagSearchSupport.toNonBlank(v);
    }
}
