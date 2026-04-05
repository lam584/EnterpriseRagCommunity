package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsTestQueryRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsTestQueryResponse;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagCommentTestQueryService {

    private final VectorIndicesRepository vectorIndicesRepository;
    private final RetrievalRagProperties ragProps;
    private final RagCommentsIndexService indexService;
    private final LlmGateway llmGateway;
    private final LlmRoutingService llmRoutingService;
    private final ObjectMapper objectMapper;
    private final SystemConfigurationService systemConfigurationService;

    public RagCommentsTestQueryResponse testQuery(Long vectorIndexId, RagCommentsTestQueryRequest req) {
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

        String fixedProviderId = RagSearchSupport.toNonBlank(vi.getMetadata() == null ? null : vi.getMetadata().get("embeddingProviderId"));

        RagEmbeddingBuildSupport.ResolvedEmbeddingTarget embeddingTarget =
                RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                        llmRoutingService,
                        overrideModel,
                        null,
                        fixedProviderId,
                        true,
                        ragProps.getEs().getEmbeddingModel()
                );
        String modelToUse = embeddingTarget.model();
        String providerToUse = embeddingTarget.providerId();

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

        String body = buildKnnSearchBody(topK, numCandidates, vec);
        JsonNode root = postSearch(indexName, body);

        List<RagCommentsTestQueryResponse.Hit> hits = new ArrayList<>();
        JsonNode arr = root.path("hits").path("hits");
        if (arr.isArray()) {
            for (JsonNode h : arr) {
                RagCommentsTestQueryResponse.Hit out = new RagCommentsTestQueryResponse.Hit();
                out.setDocId(h.path("_id").asText(null));
                if (h.hasNonNull("_score")) out.setScore(h.path("_score").asDouble());

                JsonNode src = h.path("_source");
                if (src.hasNonNull("comment_id")) out.setCommentId(src.path("comment_id").asLong());
                if (src.hasNonNull("post_id")) out.setPostId(src.path("post_id").asLong());
                if (src.hasNonNull("parent_id")) out.setParentId(src.path("parent_id").asLong());
                if (src.hasNonNull("author_id")) out.setAuthorId(src.path("author_id").asLong());
                if (src.hasNonNull("comment_floor")) out.setCommentFloor(src.path("comment_floor").asInt());
                if (src.hasNonNull("comment_level")) out.setCommentLevel(src.path("comment_level").asInt());
                out.setContentExcerpt(src.path("content_excerpt").asText(null));

                out.setContentTextPreview(RagSearchSupport.previewText(src.path("content_text").asText(null), 240));

                hits.add(out);
            }
        }

        RagCommentsTestQueryResponse resp = new RagCommentsTestQueryResponse();
        resp.setIndexName(indexName);
        resp.setTopK(topK);
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(modelToUse);
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

    private static String buildKnnSearchBody(int size, int numCandidates, float[] vec) {
        return RagSearchJsonSupport.buildPlainKnnSearchBody(size, numCandidates, vec);
    }
}
