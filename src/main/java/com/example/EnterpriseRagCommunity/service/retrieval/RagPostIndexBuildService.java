package com.example.EnterpriseRagCommunity.service.retrieval;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
        int maxChars = chunkMaxChars == null || chunkMaxChars < 200 ? 800 : Math.min(5000, chunkMaxChars);
        int overlap = chunkOverlapChars == null || chunkOverlapChars < 0 ? 80 : Math.min(maxChars - 1, chunkOverlapChars);

        Map<String, Object> meta0ForDefaults = vi.getMetadata();

        String overrideModel = toNonBlankString(embeddingModelOverride);
        String overrideProviderId = toNonBlankString(embeddingProviderId);
        boolean hasOverride = overrideModel != null && overrideProviderId != null;

        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));

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

        if (modelToUse == null) {
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
                modelToUse = legacy;
                providerToUse = null;
            } else {
                providerToUse = target.providerId();
                modelToUse = target.modelName();
            }
        }

        Integer configuredDims = expectedEmbeddingDims != null && expectedEmbeddingDims > 0 ? expectedEmbeddingDims : null;
        if (configuredDims == null) {
            Integer d = vi.getDim();
            configuredDims = d != null && d > 0 ? d : null;
        }

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

        Integer inferredDims = null;
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

                        Document d = Document.create();
                        d.setId(docId);
                        d.put("id", docId);
                        d.put("post_id", p.getId());
                        d.put("board_id", p.getBoardId());
                        d.put("author_id", p.getAuthorId());
                        d.put("chunk_index", ci);
                        d.put("content_hash", contentHash);
                        d.put("title", p.getTitle());
                        d.put("content_text", chunk);
                        if (p.getCreatedAt() != null) d.put("created_at", Date.from(p.getCreatedAt().toInstant(ZoneOffset.UTC)));
                        if (p.getUpdatedAt() != null) d.put("updated_at", Date.from(p.getUpdatedAt().toInstant(ZoneOffset.UTC)));

                        float[] vector = emb.vector();
                        if (vector.length > 0) {
                            List<Float> vec = new ArrayList<>(vector.length);
                            for (float v : vector) vec.add(v);
                            d.put("embedding", vec);
                        }

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
            if (fallbackDims != null && fallbackDims > 0) {
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
                    indexService.ensureIndex(indexName, fallbackDims);
                    ensured = true;
                } catch (Exception ex) {
                    log.warn("Ensure ES index failed without embedding inference. index={}, err={}", indexName, ex.getMessage());
                }
            }
        }

        if (dimsToUse != null && dimsToUse > 0) {
            if (vi.getDim() == null || vi.getDim() <= 0) {
                vi.setDim(dimsToUse);
            } else if (!vi.getDim().equals(dimsToUse)) {
                vi.setStatus(VectorIndexStatus.ERROR);
                vectorIndicesRepository.save(vi);
                throw new IllegalStateException("vector index dim mismatch: stored=" + vi.getDim() + ", embedding=" + dimsToUse);
            }
        }
        vi.setMetric(vi.getMetric() == null || vi.getMetric().isBlank() ? "cosine" : vi.getMetric());
        vi.setStatus(failed > 0 ? VectorIndexStatus.ERROR : VectorIndexStatus.READY);

        Map<String, Object> meta0 = vi.getMetadata();
        Map<String, Object> meta = meta0 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta0);
        meta.remove("embeddingModel");
        meta.put("esIndex", indexName);
        meta.put("sourceType", "POST");
        meta.put("lastBuildAt", LocalDateTime.now().toString());
        meta.put("lastBuildTotalPosts", totalPosts);
        meta.put("lastBuildTotalChunks", totalChunks);
        meta.put("lastBuildSuccessChunks", success);
        meta.put("lastBuildFailedChunks", failed);
        meta.put("lastBuildPostBatchSize", ps);
        if (fromPostId != null) meta.put("lastBuildFromPostId", fromPostId);
        if (lastPostId != null) meta.put("lastBuildLastPostId", lastPostId);
        if (boardId != null) meta.put("lastBuildBoardId", boardId);
        meta.put("lastBuildChunkMaxChars", maxChars);
        meta.put("lastBuildChunkOverlapChars", overlap);
        if (dimsToUse != null && dimsToUse > 0) meta.put("lastBuildEmbeddingDims", dimsToUse);
        if (modelToUse != null && !modelToUse.isBlank()) meta.put("lastBuildEmbeddingModel", modelToUse);
        if (providerToUse != null && !providerToUse.isBlank()) meta.put("lastBuildEmbeddingProviderId", providerToUse);
        if (cleared != null) meta.put("lastBuildCleared", cleared);
        if (clearError != null) meta.put("lastBuildClearError", clearError);
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

        if (modelToUse == null) {
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
                modelToUse = legacy;
                providerToUse = null;
            } else {
                providerToUse = target.providerId();
                modelToUse = target.modelName();
            }
        }

        Integer configuredDims = expectedEmbeddingDims != null && expectedEmbeddingDims > 0 ? expectedEmbeddingDims : null;
        if (configuredDims == null) {
            Integer d = vi.getDim();
            configuredDims = d != null && d > 0 ? d : null;
        }

        Integer maxCharsMeta = toInt(meta0ForDefaults == null ? null : meta0ForDefaults.get("lastBuildChunkMaxChars"));
        Integer overlapMeta = toInt(meta0ForDefaults == null ? null : meta0ForDefaults.get("lastBuildChunkOverlapChars"));
        int maxChars = chunkMaxChars == null ? (maxCharsMeta == null ? 800 : maxCharsMeta) : chunkMaxChars;
        int overlap = chunkOverlapChars == null ? (overlapMeta == null ? 80 : overlapMeta) : chunkOverlapChars;
        if (maxChars < 200) maxChars = 800;
        if (maxChars > 5000) maxChars = 5000;
        if (overlap < 0) overlap = 0;
        if (overlap >= maxChars) overlap = Math.max(0, maxChars - 1);

        String title = ModerationSampleTextUtils.normalize(p.getTitle());
        String body = ModerationSampleTextUtils.normalize(p.getContent());
        String text = (title.isBlank() ? "" : title + "\n\n") + body;
        if (text.isBlank()) return;

        List<String> chunks = splitWithOverlap(text, maxChars, overlap);
        boolean ensured = false;
        Integer inferredDims = null;
        Integer dimsToUse = null;

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

                Document d = Document.create();
                d.setId(docId);
                d.put("id", docId);
                d.put("post_id", p.getId());
                d.put("board_id", p.getBoardId());
                d.put("author_id", p.getAuthorId());
                d.put("chunk_index", ci);
                d.put("content_hash", contentHash);
                d.put("title", p.getTitle());
                d.put("content_text", chunk);
                if (p.getCreatedAt() != null) d.put("created_at", Date.from(p.getCreatedAt().toInstant(ZoneOffset.UTC)));
                if (p.getUpdatedAt() != null) d.put("updated_at", Date.from(p.getUpdatedAt().toInstant(ZoneOffset.UTC)));

                float[] vector = emb.vector();
                if (vector.length > 0) {
                    List<Float> vec = new ArrayList<>(vector.length);
                    for (float v : vector) vec.add(v);
                    d.put("embedding", vec);
                }

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

    private void deleteByQuery(String indexName, String body) {
        if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("indexName is blank");
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            URL url = new URL(endpoint + "/" + indexName.trim() + "/_delete_by_query?conflicts=proceed&refresh=true");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

            String payload = body == null ? "{\"query\":{\"match_all\":{}}}" : body;
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String json = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("ES delete_by_query HTTP " + code + ": " + json);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ES delete_by_query failed: " + e.getMessage(), e);
        }
    }

    private void deleteIndexViaHttp(String indexName) {
        if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("indexName is blank");
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            String idx = URLEncoder.encode(indexName.trim(), StandardCharsets.UTF_8);
            URL url = new URL(endpoint + "/" + idx + "?ignore_unavailable=true");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/json");
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String json = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if ((code >= 200 && code < 300) || code == 404) return;
            throw new IllegalStateException("ES delete index HTTP " + code + ": " + json);
        } catch (Exception e) {
            throw new IllegalStateException("ES delete index failed: " + e.getMessage(), e);
        }
    }

    private void touchMetadata(Long vectorIndexId, java.util.function.Consumer<Map<String, Object>> mutator) {
        if (vectorIndexId == null) return;
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId).orElse(null);
        if (vi == null) return;
        Map<String, Object> meta0 = vi.getMetadata();
        Map<String, Object> meta = meta0 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta0);
        if (mutator != null) mutator.accept(meta);
        vi.setMetadata(meta);
        vectorIndicesRepository.save(vi);
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isBlank()) return null;
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String toNonBlankString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    static void validateEmbeddingDims(Integer configuredDims, Integer inferredDims) {
        if (configuredDims != null && !configuredDims.equals(inferredDims)) {
            throw new IllegalStateException("embedding dims mismatch: configured=" + configuredDims + ", inferred=" + inferredDims);
        }
    }

    private static String summarizeException(Throwable ex) {
        if (ex == null) return null;
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage();
        String out = (msg == null || msg.isBlank()) ? type : (type + ": " + msg);
        out = out.replaceAll("\\s+", " ").trim();
        if (out.length() > 800) out = out.substring(0, 800) + "...";
        return out;
    }

    private static List<String> splitWithOverlap(String text, int maxChars, int overlap) {
        String s = text == null ? "" : text.trim();
        if (s.isBlank()) return List.of();
        if (s.length() <= maxChars) return List.of(s);

        int step = Math.max(1, maxChars - Math.max(0, overlap));
        List<String> out = new ArrayList<>();
        for (int start = 0; start < s.length(); start += step) {
            int end = Math.min(s.length(), start + maxChars);
            String part = s.substring(start, end).trim();
            if (!part.isBlank()) out.add(part);
            if (end >= s.length()) break;
        }
        return out;
    }
}
