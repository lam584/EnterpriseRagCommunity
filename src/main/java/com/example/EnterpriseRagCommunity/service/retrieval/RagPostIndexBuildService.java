package com.example.EnterpriseRagCommunity.service.retrieval;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagPostsBuildResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSampleTextUtils;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagPostIndexBuildService {

    private static final Logger log = LoggerFactory.getLogger(RagPostIndexBuildService.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final PostsRepository postsRepository;
    private final AiEmbeddingService embeddingService;
    private final LlmRoutingService llmRoutingService;
    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    private final ElasticsearchTemplate esTemplate;
    private final SystemConfigurationService systemConfigurationService;

    @SuppressWarnings("DataFlowIssue")
    @Transactional
    public RagPostsBuildResponse buildPosts(Long vectorIndexId,
                                           Long boardId,
                                           Long fromPostId,
                                           Integer postBatchSize,
                                           Integer chunkMaxChars,
                                           Integer chunkOverlapChars,
                                           Boolean clearIndex,
                                           String embeddingModelOverride,
                                           String embeddingProviderId,
                                           Integer expectedEmbeddingDims) {
        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("RagPostIndexBuildService.buildPosts skipped: APP_ES_API_KEY is missing.");
            return new RagPostsBuildResponse();
        }

        long started = System.currentTimeMillis();
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? ragProps.getEs().getIndex()
                : vi.getCollectionName().trim();

        RagPostsBuildResponse resp = new RagPostsBuildResponse();
        resp.setFromPostId(fromPostId);
        resp.setBoardId(boardId);
        resp.setPostBatchSize(postBatchSize);
        resp.setChunkMaxChars(chunkMaxChars);
        resp.setChunkOverlapChars(chunkOverlapChars);

        int ps = postBatchSize == null || postBatchSize < 1 ? 50 : Math.min(500, postBatchSize);
        RagValueSupport.ChunkingParams chunking = RagValueSupport.resolveIndexBuildChunking(chunkMaxChars, chunkOverlapChars);
        int maxChars = chunking.maxChars();
        int overlap = chunking.overlap();

        Map<String, Object> meta0ForDefaults = vi.getMetadata();

        String overrideModel = toNonBlankString(embeddingModelOverride);
        String overrideProviderId = toNonBlankString(embeddingProviderId);
        boolean hasOverride = overrideModel != null && overrideProviderId != null;

        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));

        ResolvedEmbeddingTarget resolvedTarget =
                resolveEmbeddingTarget(overrideModel, overrideProviderId, fixedProviderId, hasOverride, true);
        String modelToUse = resolvedTarget.model();
        String providerToUse = resolvedTarget.providerId();

        Integer configuredDims = resolveConfiguredDims(expectedEmbeddingDims, vi.getDim());

        vi.setStatus(VectorIndexStatus.BUILDING);
        vectorIndicesRepository.save(vi);

        boolean clearRequested = Boolean.TRUE.equals(clearIndex);
        boolean clearPending = clearRequested;
        Boolean cleared = null;
        String clearError = null;
        boolean clearedOk = false;

        long totalPosts = 0;
        long totalChunks = 0;
        long success = 0;
        long failed = 0;
        List<String> failedDocIds = new ArrayList<>();
        List<RagPostsBuildResponse.FailedDoc> failedDocs = new ArrayList<>();
        Long lastPostId = null;

        int inferredDims;
        Integer dimsToUse = null;
        boolean ensured = false;

        boolean mayRewriteExisting = fromPostId == null || fromPostId <= 0;
        boolean cleanupPerPost = mayRewriteExisting && !clearRequested;

        int page = 0;
        while (true) {
            Page<PostsEntity> batch = postsRepository.scanByStatusAndBoardFromId(
                    PostStatus.PUBLISHED,
                    boardId,
                    fromPostId,
                    PageRequest.of(page, ps, Sort.by(Sort.Direction.ASC, "id"))
            );
            if (batch.isEmpty()) break;

            for (PostsEntity p : batch.getContent()) {
                if (p == null || p.getId() == null) continue;
                lastPostId = p.getId();
                totalPosts++;

                if (cleanupPerPost) {
                    try {
                        deleteByQuery(indexName, "{\"query\":{\"term\":{\"post_id\":" + p.getId() + "}}}");
                    } catch (Exception ex) {
                        log.warn("RAG cleanup existing docs failed. postId={}, err={}", p.getId(), ex.getMessage());
                    }
                }

                String title = ModerationSampleTextUtils.normalize(p.getTitle());
                String body = ModerationSampleTextUtils.normalize(p.getContent());
                String text = (title.isBlank() ? "" : title + "\n\n") + body;
                if (text.isBlank()) continue;

                List<String> chunks = splitWithOverlap(text, maxChars, overlap);
                for (int ci = 0; ci < chunks.size(); ci++) {
                    String chunk = chunks.get(ci);
                    if (chunk == null || chunk.isBlank()) continue;
                    totalChunks++;

                    String docId = "post_" + p.getId() + "_chunk_" + ci;
                    String contentHash = ModerationSampleTextUtils.sha256Hex(chunk);

                    try {
                        AiEmbeddingService.EmbeddingResult emb = embeddingService.embedOnceForTask(chunk, modelToUse, providerToUse, LlmQueueTaskType.POST_EMBEDDING);
                        if (emb == null || emb.vector() == null) throw new IllegalStateException("embedding is null");

                        if (!ensured) {
                            inferredDims = emb.dims();
                            validateEmbeddingDims(configuredDims, inferredDims);
                            dimsToUse = configuredDims != null ? configuredDims : inferredDims;
                            if (clearPending) {
                                try {
                                    var ops = esTemplate.indexOps(IndexCoordinates.of(indexName));
                                    if (ops.exists()) {
                                        ops.delete();
                                    }
                                    cleared = true;
                                    clearedOk = true;
                                    clearPending = false;
                                } catch (Exception ex) {
                                    try {
                                        deleteIndexViaHttp(indexName);
                                        cleared = true;
                                        clearedOk = true;
                                        clearPending = false;
                                    } catch (Exception fallback) {
                                        cleared = false;
                                        clearError = "delete-index failed: " + ex.getMessage() + " | fallback http-delete-index failed: " + fallback.getMessage();
                                    }
                                }
                                if (!clearedOk) {
                                    throw new IllegalStateException("清空 ES 索引失败（需要先删除索引才能全量重建）: " + (clearError == null ? "" : clearError));
                                }
                            }
                            indexService.ensureIndex(indexName, dimsToUse);
                            ensured = true;
                        }

                        Document d = buildPostChunkDocument(p, docId, ci, contentHash, chunk, emb.vector());

                        esTemplate.save(d, IndexCoordinates.of(indexName));
                        success++;
                    } catch (Exception ex) {
                        failed++;
                        if (failedDocIds.size() < 50) failedDocIds.add(docId);
                        if (failedDocs.size() < 50) {
                            RagPostsBuildResponse.FailedDoc fd = new RagPostsBuildResponse.FailedDoc();
                            fd.setDocId(docId);
                            fd.setError(summarizeException(ex));
                            failedDocs.add(fd);
                        }
                        log.warn("RAG chunk upsert failed. docId={}, err={}", docId, summarizeException(ex), ex);
                    }
                }
            }

            if (!batch.hasNext()) break;
            page++;
        }

        if (!ensured) {
            Integer fallbackDims = configuredDims;
            if (fallbackDims == null || fallbackDims <= 0) {
                int d0 = ragProps.getEs().getEmbeddingDims();
                fallbackDims = d0 > 0 ? d0 : null;
            }
            if (fallbackDims != null) {
                try {
                    dimsToUse = fallbackDims;
                    if (clearPending) {
                        try {
                            var ops = esTemplate.indexOps(IndexCoordinates.of(indexName));
                            if (ops.exists()) {
                                ops.delete();
                            }
                            cleared = true;
                            clearedOk = true;
                        } catch (Exception ex) {
                            try {
                                deleteIndexViaHttp(indexName);
                                cleared = true;
                                clearedOk = true;
                            } catch (Exception fallback) {
                                cleared = false;
                                clearError = "delete-index failed: " + ex.getMessage() + " | fallback http-delete-index failed: " + fallback.getMessage();
                            }
                        }
                        if (!clearedOk) {
                            throw new IllegalStateException("清空 ES 索引失败（需要先删除索引才能全量重建）: " + clearError);
                        }
                    }
                    indexService.ensureIndex(indexName, fallbackDims);
                } catch (Exception ex) {
                    log.warn("Ensure ES index failed without embedding inference. index={}, err={}", indexName, ex.getMessage());
                }
            }
        }

        Map<String, Object> meta = VectorIndexMetadataSupport.prepareBuildMetadata(
                vi,
                vectorIndicesRepository,
                dimsToUse,
                failed,
                indexName,
                "POST"
        );
        meta.put("lastBuildAt", LocalDateTime.now().toString());
        meta.put("lastBuildTotalPosts", totalPosts);
        meta.put("lastBuildTotalChunks", totalChunks);
        meta.put("lastBuildSuccessChunks", success);
        meta.put("lastBuildFailedChunks", failed);
        meta.put("lastBuildPostBatchSize", ps);
        if (fromPostId != null) meta.put("lastBuildFromPostId", fromPostId);
        if (lastPostId != null) meta.put("lastBuildLastPostId", lastPostId);
        if (boardId != null) meta.put("lastBuildBoardId", boardId);
        VectorIndexMetadataSupport.putBuildEmbeddingMetadata(meta, maxChars, overlap, dimsToUse, modelToUse, providerToUse, cleared, clearError);
        vi.setMetadata(meta);
        vectorIndicesRepository.save(vi);

        resp.setTotalPosts(totalPosts);
        resp.setTotalChunks(totalChunks);
        resp.setSuccessChunks(success);
        resp.setFailedChunks(failed);
        resp.setFailedDocIds(failedDocIds.isEmpty() ? null : failedDocIds);
        resp.setFailedDocs(failedDocs.isEmpty() ? null : failedDocs);
        resp.setLastPostId(lastPostId);
        resp.setCleared(cleared);
        resp.setClearError(clearError);
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(modelToUse);
        resp.setEmbeddingProviderId(providerToUse);
        resp.setTookMs(System.currentTimeMillis() - started);
        return resp;
    }

    @Transactional
    public RagPostsBuildResponse rebuildPosts(Long vectorIndexId,
                                              Long boardId,
                                              Integer postBatchSize,
                                              Integer chunkMaxChars,
                                              Integer chunkOverlapChars,
                                              String embeddingModelOverride,
                                              String embeddingProviderId,
                                              Integer expectedEmbeddingDims) {
        RagPostsBuildResponse resp = buildPosts(vectorIndexId, boardId, null, postBatchSize, chunkMaxChars, chunkOverlapChars, true, embeddingModelOverride, embeddingProviderId, expectedEmbeddingDims);
        touchMetadata(vectorIndexId, meta -> {
            meta.put("lastRebuildAt", LocalDateTime.now().toString());
            meta.put("lastRebuildBoardId", boardId);
            meta.put("lastRebuildTookMs", resp.getTookMs());
            meta.put("lastRebuildTotalPosts", resp.getTotalPosts());
            meta.put("lastRebuildLastPostId", resp.getLastPostId());
        });
        return resp;
    }

    @Transactional
    public RagPostsBuildResponse syncPostsIncremental(Long vectorIndexId,
                                                      Long boardId,
                                                      Integer postBatchSize,
                                                      Integer chunkMaxChars,
                                                      Integer chunkOverlapChars,
                                                      String embeddingModelOverride,
                                                      String embeddingProviderId,
                                                      Integer expectedEmbeddingDims) {
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        Map<String, Object> meta0 = vi.getMetadata();
        Long last = toLong(meta0 == null ? null : meta0.get("lastSyncLastPostId"));
        if (last == null) last = toLong(meta0 == null ? null : meta0.get("lastBuildLastPostId"));
        if (last == null) last = 0L;

        Long from = last > 0 ? last + 1 : null;
        RagPostsBuildResponse resp = buildPosts(vectorIndexId, boardId, from, postBatchSize, chunkMaxChars, chunkOverlapChars, false, embeddingModelOverride, embeddingProviderId, expectedEmbeddingDims);

        Long newLast = resp.getLastPostId() == null ? last : resp.getLastPostId();
        boolean noop = resp.getTotalPosts() == 0;
        touchMetadata(vectorIndexId, meta -> {
            meta.put("lastSyncAt", LocalDateTime.now().toString());
            meta.put("lastSyncBoardId", boardId);
            meta.put("lastSyncFromPostId", from);
            meta.put("lastSyncLastPostId", newLast);
            meta.put("lastSyncTookMs", resp.getTookMs());
            meta.put("lastSyncTotalPosts", resp.getTotalPosts());
            meta.put("lastSyncNoop", noop);
        });
        return resp;
    }

    public void syncSinglePost(Long vectorIndexId, Long postId) {
        syncSinglePost(vectorIndexId, postId, null, null, null, null, null);
    }

    private static Integer toInt(Object v) {
        switch (v) {
            case null -> {
                return null;
            }
            case Number n -> {
                return n.intValue();
            }
            case String s -> {
                String t = s.trim();
                if (t.isBlank()) return null;
                try {
                    return Integer.parseInt(t);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            default -> {
            }
        }
        return null;
    }

    private void deleteByQuery(String indexName, String body) {
        ElasticsearchHttpSupport.deleteByQuery(systemConfigurationService, indexName, body);
    }

    private void deleteIndexViaHttp(String indexName) {
        ElasticsearchHttpSupport.deleteIndex(systemConfigurationService, indexName);
    }

    private void touchMetadata(Long vectorIndexId, java.util.function.Consumer<Map<String, Object>> mutator) {
        VectorIndexMetadataSupport.touchMetadata(vectorIndexId, vectorIndicesRepository, mutator);
    }

    private static Long toLong(Object v) {
        return RagValueSupport.toLong(v);
    }

    public void syncSinglePost(Long vectorIndexId,
                               Long postId,
                               Integer chunkMaxChars,
                               Integer chunkOverlapChars,
                               String embeddingModelOverride,
                               String embeddingProviderId,
                               Integer expectedEmbeddingDims) {
        if (vectorIndexId == null || postId == null) return;

        String apiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return; // Skip if ES is not configured
        }

        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? ragProps.getEs().getIndex()
                : vi.getCollectionName().trim();

        try {
            deleteByQuery(indexName, "{\"query\":{\"term\":{\"post_id\":" + postId + "}}}");
        } catch (Exception ex) {
            log.warn("RAG delete stale post docs failed. vectorIndexId={}, postId={}, err={}", vectorIndexId, postId, ex.getMessage());
        }

        PostsEntity p = postsRepository.findById(postId).orElse(null);
        if (p == null || Boolean.TRUE.equals(p.getIsDeleted()) || p.getStatus() != PostStatus.PUBLISHED) {
            return;
        }

        Map<String, Object> meta0ForDefaults = vi.getMetadata();

        String overrideModel = toNonBlankString(embeddingModelOverride);
        String overrideProviderId = toNonBlankString(embeddingProviderId);
        boolean hasOverride = overrideModel != null && overrideProviderId != null;

        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));

        ResolvedEmbeddingTarget resolvedTarget =
                resolveEmbeddingTarget(overrideModel, overrideProviderId, fixedProviderId, hasOverride, false);
        String modelToUse = resolvedTarget.model();
        String providerToUse = resolvedTarget.providerId();

        Integer configuredDims = resolveConfiguredDims(expectedEmbeddingDims, vi.getDim());

        Integer maxCharsMeta = toInt(meta0ForDefaults == null ? null : meta0ForDefaults.get("lastBuildChunkMaxChars"));
        Integer overlapMeta = toInt(meta0ForDefaults == null ? null : meta0ForDefaults.get("lastBuildChunkOverlapChars"));
        RagValueSupport.ChunkingParams chunking = RagValueSupport.resolveIndexBuildChunking(
                chunkMaxChars == null ? maxCharsMeta : chunkMaxChars,
                chunkOverlapChars == null ? overlapMeta : chunkOverlapChars
        );
        int maxChars = chunking.maxChars();
        int overlap = chunking.overlap();

        String title = ModerationSampleTextUtils.normalize(p.getTitle());
        String body = ModerationSampleTextUtils.normalize(p.getContent());
        String text = (title.isBlank() ? "" : title + "\n\n") + body;
        if (text.isBlank()) return;

        List<String> chunks = splitWithOverlap(text, maxChars, overlap);
        boolean ensured = false;
        int inferredDims;
        int dimsToUse;

        for (int ci = 0; ci < chunks.size(); ci++) {
            String chunk = chunks.get(ci);
            if (chunk == null || chunk.isBlank()) continue;

            String docId = "post_" + p.getId() + "_chunk_" + ci;
            String contentHash = ModerationSampleTextUtils.sha256Hex(chunk);
            try {
                AiEmbeddingService.EmbeddingResult emb = embeddingService.embedOnceForTask(chunk, modelToUse, providerToUse, LlmQueueTaskType.POST_EMBEDDING);
                if (emb == null || emb.vector() == null) throw new IllegalStateException("embedding is null");

                if (!ensured) {
                    inferredDims = emb.dims();
                    validateEmbeddingDims(configuredDims, inferredDims);
                    dimsToUse = configuredDims != null ? configuredDims : inferredDims;
                    indexService.ensureIndex(indexName, dimsToUse);
                    ensured = true;
                }

                Document d = buildPostChunkDocument(p, docId, ci, contentHash, chunk, emb.vector());

                esTemplate.save(d, IndexCoordinates.of(indexName));
            } catch (Exception ex) {
                log.warn("RAG single-post chunk upsert failed. vectorIndexId={}, postId={}, docId={}, err={}", vectorIndexId, postId, docId, summarizeException(ex), ex);
            }
        }

        try {
            esTemplate.indexOps(IndexCoordinates.of(indexName)).refresh();
        } catch (Exception ignored) {
        }
    }

    private static String toNonBlankString(Object v) {
        return RagSearchSupport.toNonBlank(v);
    }

    private static Document buildPostChunkDocument(
            PostsEntity post,
            String docId,
            int chunkIndex,
            String contentHash,
            String chunk,
            float[] vector
    ) {
        Document d = Document.create();
        d.setId(docId);
        d.put("id", docId);
        d.put("post_id", post.getId());
        d.put("board_id", post.getBoardId());
        d.put("author_id", post.getAuthorId());
        d.put("chunk_index", chunkIndex);
        d.put("content_hash", contentHash);
        d.put("title", post.getTitle());
        d.put("content_text", chunk);
        if (post.getCreatedAt() != null) d.put("created_at", Date.from(post.getCreatedAt().toInstant(ZoneOffset.UTC)));
        if (post.getUpdatedAt() != null) d.put("updated_at", Date.from(post.getUpdatedAt().toInstant(ZoneOffset.UTC)));
        List<Float> embedding = toFloatList(vector);
        if (!embedding.isEmpty()) {
            d.put("embedding", embedding);
        }
        return d;
    }

    private static List<Float> toFloatList(float[] vector) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        List<Float> out = new ArrayList<>(vector.length);
        for (float value : vector) {
            out.add(value);
        }
        return out;
    }

    private ResolvedEmbeddingTarget resolveEmbeddingTarget(String overrideModel,
                                                           String overrideProviderId,
                                                           String fixedProviderId,
                                                           boolean hasOverride,
                                                           boolean requireModelForProviderOverride) {
        RagEmbeddingBuildSupport.SelectedEmbeddingTarget selectedTarget =
                RagEmbeddingBuildSupport.preselectTarget(
                        overrideModel,
                        overrideProviderId,
                        fixedProviderId,
                        requireModelForProviderOverride
                );
        String modelToUse = selectedTarget.model();
        String providerToUse = selectedTarget.providerId();

        if (modelToUse != null) {
            return new ResolvedEmbeddingTarget(modelToUse, providerToUse);
        }

        LlmRoutingService.RouteTarget target = (providerToUse == null)
                ? llmRoutingService.pickNext(LlmQueueTaskType.POST_EMBEDDING, new HashSet<>())
                : llmRoutingService.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, providerToUse, new HashSet<>());
        if (target == null) {
            String legacy = toNonBlankString(ragProps.getEs().getEmbeddingModel());
            if (legacy == null) {
                throw new IllegalStateException(providerToUse == null
                        ? "no eligible embedding target (please check embedding routing config)"
                        : ("no eligible embedding target for providerId=" + providerToUse + " (please check embedding routing config)"));
            }
            return new ResolvedEmbeddingTarget(legacy, null);
        }
        return new ResolvedEmbeddingTarget(target.modelName(), target.providerId());
    }

    static void validateEmbeddingDims(Integer configuredDims, Integer inferredDims) {
        if (configuredDims != null && !configuredDims.equals(inferredDims)) {
            throw new IllegalStateException("embedding dims mismatch: configured=" + configuredDims + ", inferred=" + inferredDims);
        }
    }

    private static Integer resolveConfiguredDims(Integer expectedEmbeddingDims, Integer vectorIndexDims) {
        return RagEmbeddingBuildSupport.resolveConfiguredDims(expectedEmbeddingDims, vectorIndexDims);
    }

    private static String summarizeException(Throwable ex) {
        return ExceptionSummaryUtils.summarizeException(ex);
    }

    private static List<String> splitWithOverlap(String text, int maxChars, int overlap) {
        return RagValueSupport.splitWithOverlap(text, maxChars, overlap);
    }

    private record ResolvedEmbeddingTarget(String model, String providerId) {
    }
}
