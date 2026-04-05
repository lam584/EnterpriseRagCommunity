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

import java.util.ArrayList;
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
        RagSearchSupport.requireVectorIndexId(vectorIndexId);
        RagSearchSupport.requireRequest(req);
        String q = RagSearchSupport.requireQueryText(req.getQueryText());
        int topK = RagSearchSupport.clampTopK(req.getTopK());
        int numCandidates = RagSearchSupport.resolveNumCandidates(req.getNumCandidates(), topK);

        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? ragProps.getEs().getIndex()
                : vi.getCollectionName().trim();

        long started = System.currentTimeMillis();

        String overrideModel = RagSearchSupport.toNonBlank(req.getEmbeddingModel());
        String overrideProviderId = RagSearchSupport.toNonBlank(req.getEmbeddingProviderId());
        boolean hasOverride = overrideModel != null && overrideProviderId != null;

        String fixedProviderId = RagSearchSupport.toNonBlank(vi.getMetadata() == null ? null : vi.getMetadata().get("embeddingProviderId"));

        RagEmbeddingBuildSupport.ResolvedEmbeddingTarget embeddingTarget;
        try {
            embeddingTarget = RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                llmRoutingService,
                overrideModel,
                overrideProviderId,
                fixedProviderId,
                false,
                ragProps.getEs().getEmbeddingModel()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
        }
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
                dims -> indexService.ensureIndex(indexName, dims),
                vectorIndicesRepository::save
        );

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

                out.setContentTextPreview(RagSearchSupport.previewText(src.path("content_text").asText(null), 240));

                hits.add(out);
            }
        }

        RagPostsTestQueryResponse resp = new RagPostsTestQueryResponse();
        resp.setIndexName(indexName);
        resp.setTopK(topK);
        resp.setBoardId(req.getBoardId());
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

    private static String buildKnnSearchBody(int size, int numCandidates, Long boardId, float[] vec) {
        return RagPostSearchJsonSupport.buildKnnSearchBody(size, numCandidates, boardId, vec);
    }
}
