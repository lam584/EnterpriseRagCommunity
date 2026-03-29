package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.RagCommentsBuildResponse;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSampleTextUtils;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class RagCommentIndexBuildService {

    private static final Logger log = LoggerFactory.getLogger(RagCommentIndexBuildService.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final CommentsRepository commentsRepository;
    private final AiEmbeddingService embeddingService;
    private final LlmRoutingService llmRoutingService;
    private final RetrievalRagProperties ragProps;
    private final RagCommentsIndexService indexService;
    private final ElasticsearchTemplate esTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigurationService systemConfigurationService;

    @Transactional
    public RagCommentsBuildResponse buildComments(Long vectorIndexId,
                                                 Long fromCommentId,
                                                 Integer commentBatchSize,
                                                 Integer chunkMaxChars,
                                                 Integer chunkOverlapChars,
                                                 Boolean clearIndex,
                                                 String embeddingModelOverride,
                                                 Integer expectedEmbeddingDims) {
        long started = System.currentTimeMillis();
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? indexService.defaultIndexName()
                : vi.getCollectionName().trim();

        RagCommentsBuildResponse resp = new RagCommentsBuildResponse();
        resp.setFromCommentId(fromCommentId);
        resp.setCommentBatchSize(commentBatchSize);
        resp.setChunkMaxChars(chunkMaxChars);
        resp.setChunkOverlapChars(chunkOverlapChars);

        int ps = commentBatchSize == null || commentBatchSize < 1 ? 100 : Math.min(1000, commentBatchSize);
        int maxChars = chunkMaxChars == null || chunkMaxChars < 200 ? 800 : Math.min(5000, chunkMaxChars);
        int overlap = chunkOverlapChars == null || chunkOverlapChars < 0 ? 80 : Math.min(maxChars - 1, chunkOverlapChars);

        Map<String, Object> meta0ForDefaults = vi.getMetadata();
        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));
        String modelToUse = toNonBlankString(embeddingModelOverride);
        if (modelToUse == null) modelToUse = toNonBlankString(ragProps.getEs().getEmbeddingModel());

        String providerToUse = fixedProviderId;
        if (modelToUse == null) {
            LlmRoutingService.RouteTarget target = (providerToUse == null)
                    ? llmRoutingService.pickNext(LlmQueueTaskType.POST_EMBEDDING, new HashSet<>())
                    : llmRoutingService.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, providerToUse, new HashSet<>());
            if (target == null) {
                throw new IllegalStateException(providerToUse == null
                        ? "no eligible embedding target (please check embedding routing config)"
                        : ("no eligible embedding target for providerId=" + providerToUse + " (please check embedding routing config)"));
            }
            providerToUse = target.providerId();
            modelToUse = target.modelName();
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

        Map<Long, Integer> perPostMaxFloor = new LinkedHashMap<>();
        Map<Long, Integer> existingMaxFloorCache = new LinkedHashMap<>();
        Map<Long, Long> parentCache = new LinkedHashMap<>();
        Map<Long, Integer> existingCommentFloorCache = new LinkedHashMap<>();

        long totalComments = 0;
        long totalChunks = 0;
        long successChunks = 0;
        long failedChunks = 0;
        List<String> failedDocIds = new ArrayList<>();
        List<RagCommentsBuildResponse.FailedDoc> failedDocs = new ArrayList<>();

        boolean ensured = false;
        Long lastId = null;
        Long cursor = fromCommentId;
        while (true) {
            Page<CommentsEntity> page = commentsRepository.scanVisibleFromId(cursor, PageRequest.of(0, ps, Sort.by(Sort.Direction.ASC, "id")));
            if (page == null || page.isEmpty()) break;

            for (CommentsEntity c : page.getContent()) {
                if (c == null) continue;
                lastId = c.getId();
                cursor = lastId;

                if (c.getIsDeleted() != null && c.getIsDeleted()) continue;
                if (c.getStatus() != CommentStatus.VISIBLE) continue;

                totalComments++;

                Long commentId = c.getId();
                Long postId = c.getPostId();
                Long parentId = c.getParentId();
                Long authorId = c.getAuthorId();
                String excerpt = RagCommentDerivedFields.excerpt(c.getContent(), 160);

                Integer commentFloor = clearRequested ? null : existingCommentFloorCache.get(commentId);
                if (!clearRequested && commentFloor == null) {
                    commentFloor = findExistingCommentFloor(indexName, commentId);
                    if (commentFloor != null) {
                        existingCommentFloorCache.put(commentId, commentFloor);
                    }
                }
                if (commentFloor == null) {
                    Integer existingMax = existingMaxFloorCache.get(postId);
                    if (!clearRequested && existingMax == null) {
                        existingMax = findExistingPostMaxFloor(indexName, postId);
                        existingMaxFloorCache.put(postId, existingMax);
                    }
                    commentFloor = RagCommentDerivedFields.nextFloor(perPostMaxFloor, postId, existingMax);
                } else {
                    Integer cur = perPostMaxFloor.get(postId);
                    if (cur == null || cur < commentFloor) perPostMaxFloor.put(postId, commentFloor);
                }

                int commentLevel = RagCommentDerivedFields.computeLevel(parentId, (long pid) -> {
                    Long v = parentCache.get(pid);
                    if (v != null || parentCache.containsKey(pid)) return v;
                    CommentsEntity p = commentsRepository.findById(pid).orElse(null);
                    Long out = p == null ? null : p.getParentId();
                    parentCache.put(pid, out);
                    return out;
                });

                String content = ModerationSampleTextUtils.normalize(c.getContent());
                if (content == null || content.isBlank()) continue;
                List<String> parts = splitWithOverlap(content, maxChars, overlap);
                if (parts.isEmpty()) continue;

                for (int ci = 0; ci < parts.size(); ci++) {
                    String chunk = parts.get(ci);
                    if (chunk == null || chunk.isBlank()) continue;
                    totalChunks++;

                    String docId = "comment_" + commentId + "_chunk_" + ci;
                    try {
                        AiEmbeddingService.EmbeddingResult emb = embeddingService.embedOnceForTask(chunk, modelToUse, providerToUse, LlmQueueTaskType.POST_EMBEDDING);
                        if (emb == null || emb.vector() == null || emb.vector().length == 0) {
                            throw new IllegalStateException("embedding returned empty vector");
                        }
                        int inferredDims = emb.vector().length;
                        validateEmbeddingDims(configuredDims, inferredDims);
                        int dimsToUse = configuredDims != null ? configuredDims : inferredDims;
                        if (!ensured) {
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
                                    } catch (Exception ex2) {
                                        cleared = false;
                                        clearError = summarizeException(ex2);
                                    }
                                }
                                if (!clearedOk) {
                                    throw new IllegalStateException("清空 ES 索引失败（需要先删除索引才能全量重建）: " + (clearError == null ? "" : clearError));
                                }
                            }
                            indexService.ensureIndex(indexName, dimsToUse);
                            ensured = true;
                        }

                        String contentHash = ModerationSampleTextUtils.sha256Hex(chunk);

                        Document d = Document.create();
                        d.setId(docId);
                        d.put("id", docId);
                        d.put("comment_id", commentId);
                        d.put("post_id", postId);
                        if (parentId != null) d.put("parent_id", parentId);
                        if (authorId != null) d.put("author_id", authorId);
                        d.put("comment_floor", commentFloor);
                        d.put("comment_level", commentLevel);
                        d.put("source_type", "comment");
                        d.put("chunk_index", ci);
                        d.put("content_hash", contentHash);
                        if (excerpt != null) d.put("content_excerpt", excerpt);
                        d.put("content_text", chunk);
                        if (c.getCreatedAt() != null) d.put("created_at", Date.from(c.getCreatedAt().toInstant(ZoneOffset.UTC)));
                        if (c.getUpdatedAt() != null) d.put("updated_at", Date.from(c.getUpdatedAt().toInstant(ZoneOffset.UTC)));

                        float[] vector = emb.vector();
                        List<Float> vec = new ArrayList<>(vector.length);
                        for (float v : vector) vec.add(v);
                        d.put("embedding", vec);

                        esTemplate.save(d, IndexCoordinates.of(indexName));
                        successChunks++;
                    } catch (Exception ex) {
                        failedChunks++;
                        failedDocIds.add(docId);
                        RagCommentsBuildResponse.FailedDoc fd = new RagCommentsBuildResponse.FailedDoc();
                        fd.setDocId(docId);
                        fd.setError(summarizeException(ex));
                        failedDocs.add(fd);
                        log.warn("RAG comment chunk upsert failed. vectorIndexId={}, commentId={}, docId={}, err={}", vectorIndexId, commentId, docId, summarizeException(ex), ex);
                    }
                }
            }

            if (!page.hasNext()) break;
        }

        if (!ensured) {
            Integer fallbackDims = configuredDims;
            if (fallbackDims == null || fallbackDims <= 0) {
                int d0 = ragProps.getEs().getEmbeddingDims();
                fallbackDims = d0 > 0 ? d0 : null;
            }
            if (fallbackDims != null && fallbackDims > 0) {
                try {
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
                            } catch (Exception ex2) {
                                cleared = false;
                                clearError = summarizeException(ex2);
                            }
                        }
                        if (!clearedOk) {
                            throw new IllegalStateException("清空 ES 索引失败（需要先删除索引才能全量重建）: " + (clearError == null ? "" : clearError));
                        }
                    }
                    indexService.ensureIndex(indexName, fallbackDims);
                    ensured = true;
                } catch (Exception ex) {
                    log.warn("Ensure ES comment index failed without embedding inference. index={}, err={}", indexName, ex.getMessage());
                }
            }
        }

        try {
            esTemplate.indexOps(IndexCoordinates.of(indexName)).refresh();
        } catch (Exception ignored) {
        }

        if (vi.getDim() == null || vi.getDim() <= 0) {
            if (configuredDims != null) {
                vi.setDim(configuredDims);
            }
        }

        vi.setStatus(VectorIndexStatus.READY);
        vectorIndicesRepository.save(vi);

        resp.setTotalComments(totalComments);
        resp.setTotalChunks(totalChunks);
        resp.setSuccessChunks(successChunks);
        resp.setFailedChunks(failedChunks);
        resp.setFailedDocIds(failedDocIds);
        resp.setFailedDocs(failedDocs);
        resp.setLastCommentId(lastId);
        resp.setChunkMaxChars(maxChars);
        resp.setChunkOverlapChars(overlap);
        resp.setEmbeddingModel(modelToUse);
        resp.setEmbeddingDims(configuredDims != null ? configuredDims : (vi.getDim() == null ? null : vi.getDim()));
        resp.setCleared(cleared);
        resp.setClearError(clearError);
        resp.setTookMs(System.currentTimeMillis() - started);

        long totalComments0 = totalComments;
        long totalChunks0 = totalChunks;
        long successChunks0 = successChunks;
        long failedChunks0 = failedChunks;
        Long lastId0 = lastId;
        int ps0 = ps;
        int maxChars0 = maxChars;
        int overlap0 = overlap;
        Integer embeddingDims0 = resp.getEmbeddingDims();
        String modelToUse0 = modelToUse;
        Boolean cleared0 = cleared;
        String clearError0 = clearError;
        Long fromCommentId0 = fromCommentId;

        touchMetadata(vectorIndexId, meta -> {
            meta.remove("embeddingModel");
            meta.put("sourceType", "COMMENT");
            meta.put("esIndex", indexName);
            meta.put("lastBuildAt", LocalDateTime.now().toString());
            meta.put("lastBuildTotalComments", totalComments0);
            meta.put("lastBuildTotalChunks", totalChunks0);
            meta.put("lastBuildSuccessChunks", successChunks0);
            meta.put("lastBuildFailedChunks", failedChunks0);
            meta.put("lastBuildCommentBatchSize", ps0);
            meta.put("lastBuildFromCommentId", fromCommentId0);
            meta.put("lastBuildLastCommentId", lastId0);
            meta.put("lastBuildChunkMaxChars", maxChars0);
            meta.put("lastBuildChunkOverlapChars", overlap0);
            meta.put("lastBuildEmbeddingDims", embeddingDims0);
            meta.put("lastBuildEmbeddingModel", modelToUse0);
            meta.put("lastBuildCleared", cleared0);
            meta.put("lastBuildClearError", clearError0);
            meta.put("lastSyncAt", LocalDateTime.now().toString());
            meta.put("lastSyncLastCommentId", lastId0);
        });

        return resp;
    }

    @Transactional
    public RagCommentsBuildResponse rebuildComments(Long vectorIndexId,
                                                   Integer commentBatchSize,
                                                   Integer chunkMaxChars,
                                                   Integer chunkOverlapChars,
                                                   String embeddingModelOverride,
                                                   Integer expectedEmbeddingDims) {
        return buildComments(vectorIndexId, null, commentBatchSize, chunkMaxChars, chunkOverlapChars, true, embeddingModelOverride, expectedEmbeddingDims);
    }

    @Transactional
    public RagCommentsBuildResponse syncCommentsIncremental(Long vectorIndexId,
                                                           Integer commentBatchSize,
                                                           Integer chunkMaxChars,
                                                           Integer chunkOverlapChars,
                                                           String embeddingModelOverride,
                                                           Integer expectedEmbeddingDims) {
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));
        Long last = toLong(vi.getMetadata() == null ? null : vi.getMetadata().get("lastSyncLastCommentId"));
        return buildComments(vectorIndexId, last, commentBatchSize, chunkMaxChars, chunkOverlapChars, false, embeddingModelOverride, expectedEmbeddingDims);
    }

    @Transactional
    public void syncSingleComment(Long vectorIndexId, Long commentId) {
        if (vectorIndexId == null) throw new IllegalArgumentException("vectorIndexId is required");
        if (commentId == null) throw new IllegalArgumentException("commentId is required");
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? indexService.defaultIndexName()
                : vi.getCollectionName().trim();

        try {
            deleteByQuery(indexName, "{\"query\":{\"term\":{\"comment_id\":" + commentId + "}}}");
        } catch (Exception ex) {
            log.warn("RAG single-comment delete failed. vectorIndexId={}, commentId={}, err={}", vectorIndexId, commentId, summarizeException(ex), ex);
        }

        CommentsEntity c = commentsRepository.findById(commentId).orElse(null);
        if (c == null) return;
        if (Boolean.TRUE.equals(c.getIsDeleted()) || c.getStatus() != CommentStatus.VISIBLE) {
            return;
        }

        String modelToUse = null;
        String providerToUse = toNonBlankString(vi.getMetadata() == null ? null : vi.getMetadata().get("embeddingProviderId"));
        if (modelToUse == null) modelToUse = toNonBlankString(ragProps.getEs().getEmbeddingModel());

        if (modelToUse == null) {
            LlmRoutingService.RouteTarget target = (providerToUse == null)
                    ? llmRoutingService.pickNext(LlmQueueTaskType.POST_EMBEDDING, new HashSet<>())
                    : llmRoutingService.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, providerToUse, new HashSet<>());
            if (target == null) {
                throw new IllegalStateException(providerToUse == null
                        ? "no eligible embedding target (please check embedding routing config)"
                        : ("no eligible embedding target for providerId=" + providerToUse + " (please check embedding routing config)"));
            }
            providerToUse = target.providerId();
            modelToUse = target.modelName();
        }

        Integer configuredDims = null;
        Integer d = vi.getDim();
        configuredDims = d != null && d > 0 ? d : null;

        int maxChars = toInt(vi.getMetadata() == null ? null : vi.getMetadata().get("lastBuildChunkMaxChars"));
        if (maxChars <= 0) maxChars = 800;
        int overlap = toInt(vi.getMetadata() == null ? null : vi.getMetadata().get("lastBuildChunkOverlapChars"));
        if (overlap < 0) overlap = 80;
        if (overlap >= maxChars) overlap = Math.max(0, maxChars - 1);

        String content = ModerationSampleTextUtils.normalize(c.getContent());
        if (content == null || content.isBlank()) return;
        List<String> parts = splitWithOverlap(content, maxChars, overlap);
        if (parts.isEmpty()) return;

        Long postId = c.getPostId();
        Integer floor = findExistingCommentFloor(indexName, commentId);
        if (floor == null) {
            Integer existingMax = findExistingPostMaxFloor(indexName, postId);
            Map<Long, Integer> counter = new LinkedHashMap<>();
            floor = RagCommentDerivedFields.nextFloor(counter, postId, existingMax);
        }
        int level = RagCommentDerivedFields.computeLevel(c.getParentId(), (long pid) -> {
            CommentsEntity p = commentsRepository.findById(pid).orElse(null);
            return p == null ? null : p.getParentId();
        });
        String excerpt = RagCommentDerivedFields.excerpt(c.getContent(), 160);

        boolean ensured = false;
        for (int ci = 0; ci < parts.size(); ci++) {
            String chunk = parts.get(ci);
            if (chunk == null || chunk.isBlank()) continue;
            String docId = "comment_" + commentId + "_chunk_" + ci;
            try {
                AiEmbeddingService.EmbeddingResult emb = embeddingService.embedOnceForTask(chunk, modelToUse, providerToUse, LlmQueueTaskType.POST_EMBEDDING);
                if (emb == null || emb.vector() == null || emb.vector().length == 0) {
                    throw new IllegalStateException("embedding returned empty vector");
                }
                int inferredDims = emb.vector().length;
                validateEmbeddingDims(configuredDims, inferredDims);
                int dimsToUse = configuredDims != null ? configuredDims : inferredDims;
                if (!ensured) {
                    indexService.ensureIndex(indexName, dimsToUse);
                    ensured = true;
                }

                String contentHash = ModerationSampleTextUtils.sha256Hex(chunk);

                Document d0 = Document.create();
                d0.setId(docId);
                d0.put("id", docId);
                d0.put("comment_id", commentId);
                d0.put("post_id", postId);
                if (c.getParentId() != null) d0.put("parent_id", c.getParentId());
                if (c.getAuthorId() != null) d0.put("author_id", c.getAuthorId());
                d0.put("comment_floor", floor);
                d0.put("comment_level", level);
                d0.put("source_type", "comment");
                d0.put("chunk_index", ci);
                d0.put("content_hash", contentHash);
                if (excerpt != null) d0.put("content_excerpt", excerpt);
                d0.put("content_text", chunk);
                if (c.getCreatedAt() != null) d0.put("created_at", Date.from(c.getCreatedAt().toInstant(ZoneOffset.UTC)));
                if (c.getUpdatedAt() != null) d0.put("updated_at", Date.from(c.getUpdatedAt().toInstant(ZoneOffset.UTC)));

                float[] vector = emb.vector();
                List<Float> vec = new ArrayList<>(vector.length);
                for (float v : vector) vec.add(v);
                d0.put("embedding", vec);

                esTemplate.save(d0, IndexCoordinates.of(indexName));
            } catch (Exception ex) {
                log.warn("RAG single-comment chunk upsert failed. vectorIndexId={}, commentId={}, docId={}, err={}", vectorIndexId, commentId, docId, summarizeException(ex), ex);
            }
        }

        try {
            esTemplate.indexOps(IndexCoordinates.of(indexName)).refresh();
        } catch (Exception ignored) {
        }
    }

    private Integer findExistingCommentFloor(String indexName, Long commentId) {
        if (indexName == null || indexName.isBlank()) return null;
        if (commentId == null) return null;
        String body = "{\"size\":1,\"query\":{\"term\":{\"comment_id\":" + commentId + "}},\"_source\":[\"comment_floor\"]}";
        JsonNode root = postSearch(indexName, body, "hits.hits._source.comment_floor");
        JsonNode hits = root.path("hits").path("hits");
        if (hits.isArray() && hits.size() > 0) {
            JsonNode src = hits.get(0).path("_source");
            if (src.hasNonNull("comment_floor")) return src.path("comment_floor").asInt();
        }
        return null;
    }

    private Integer findExistingPostMaxFloor(String indexName, Long postId) {
        if (indexName == null || indexName.isBlank()) return null;
        if (postId == null) return null;
        String body = "{\"size\":0,\"query\":{\"bool\":{\"filter\":[{\"term\":{\"post_id\":" + postId + "}}]}},\"aggs\":{\"max_floor\":{\"max\":{\"field\":\"comment_floor\"}}}}";
        JsonNode root = postSearch(indexName, body, "aggregations.max_floor.value");
        JsonNode v = root.path("aggregations").path("max_floor").path("value");
        if (v.isNumber()) return v.asInt();
        if (v.isTextual()) {
            String t = v.asText().trim();
            if (!t.isBlank()) {
                try {
                    return (int) Double.parseDouble(t);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private JsonNode postSearch(String indexName, String body, String filterPath) {
        String elasticsearchUris = systemConfigurationService.getConfig("spring.elasticsearch.uris");
        String endpoint = elasticsearchUris;
        if (endpoint == null || endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
        if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

        String fp = filterPath == null || filterPath.isBlank() ? null : filterPath.trim();
        String url = endpoint + "/" + indexName + "/_search";
        if (fp != null) {
            url = url + "?filter_path=" + URLEncoder.encode(fp, StandardCharsets.UTF_8);
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
            if (is == null) return objectMapper.createObjectNode();
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) return objectMapper.createObjectNode();
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void deleteByQuery(String indexName, String body) {
        if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("indexName is blank");
        String elasticsearchUris = systemConfigurationService.getConfig("spring.elasticsearch.uris");
        String endpoint = elasticsearchUris;
        if (endpoint == null || endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
        if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

        try {
            URL url = new URL(endpoint + "/" + indexName.trim() + "/_delete_by_query?conflicts=proceed&refresh=true");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String elasticsearchApiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey.trim());
            }

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
        String elasticsearchUris = systemConfigurationService.getConfig("spring.elasticsearch.uris");
        String endpoint = elasticsearchUris;
        if (endpoint == null || endpoint.isBlank()) endpoint = "http://127.0.0.1:9200";
        if (endpoint.contains(",")) endpoint = endpoint.split(",")[0].trim();
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);

        try {
            String idx = URLEncoder.encode(indexName.trim(), StandardCharsets.UTF_8);
            URL url = new URL(endpoint + "/" + idx + "?ignore_unavailable=true");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/json");

            String elasticsearchApiKey = systemConfigurationService.getConfig("APP_ES_API_KEY");
            if (elasticsearchApiKey != null && !elasticsearchApiKey.isBlank()) {
                conn.setRequestProperty("Authorization", "ApiKey " + elasticsearchApiKey.trim());
            }

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

    private static int toInt(Object v) {
        Integer out = toIntBoxed(v);
        return out == null ? 0 : out;
    }

    private static Integer toIntBoxed(Object v) {
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
