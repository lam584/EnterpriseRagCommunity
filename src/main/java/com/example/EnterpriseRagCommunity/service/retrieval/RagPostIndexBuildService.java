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
    private final RetrievalRagProperties ragProps;
    private final RagPostsIndexService indexService;
    private final ElasticsearchTemplate esTemplate;

    @Value("${spring.elasticsearch.uris:http://127.0.0.1:9200}")
    private String elasticsearchUris;

    @Value("${app.es.api-key:}")
    private String elasticsearchApiKey;

    @Transactional
    public RagPostsBuildResponse buildPosts(Long vectorIndexId,
                                           Long boardId,
                                           Long fromPostId,
                                           Integer postBatchSize,
                                           Integer chunkMaxChars,
                                           Integer chunkOverlapChars,
                                           Boolean clearIndex,
                                           String embeddingModelOverride,
                                           Integer expectedEmbeddingDims) {
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
        String metaModel = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingModel"));
        if (metaModel == null) metaModel = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("lastBuildEmbeddingModel"));
        String modelToUse = toNonBlankString(embeddingModelOverride);
        if (modelToUse == null) modelToUse = metaModel;
        if (modelToUse == null) modelToUse = toNonBlankString(ragProps.getEs().getEmbeddingModel());

        Integer configuredDims = expectedEmbeddingDims != null && expectedEmbeddingDims > 0 ? expectedEmbeddingDims : null;
        if (configuredDims == null) {
            Integer d = vi.getDim();
            configuredDims = d != null && d > 0 ? d : null;
        }

        vi.setStatus(VectorIndexStatus.BUILDING);
        vectorIndicesRepository.save(vi);

        Boolean cleared = null;
        String clearError = null;
        boolean clearedOk = false;
        if (Boolean.TRUE.equals(clearIndex)) {
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
                throw new IllegalStateException("清空 ES 索引失败（需要先删除索引才能全量重建）: " + (clearError == null ? "" : clearError));
            }
        }

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
        boolean cleanupPerPost = mayRewriteExisting && !clearedOk;

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
                        AiEmbeddingService.EmbeddingResult emb = embeddingService.embedOnce(chunk, modelToUse);
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
                                              Integer expectedEmbeddingDims) {
        RagPostsBuildResponse resp = buildPosts(vectorIndexId, boardId, null, postBatchSize, chunkMaxChars, chunkOverlapChars, true, embeddingModelOverride, expectedEmbeddingDims);
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
                                                      Integer expectedEmbeddingDims) {
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        Map<String, Object> meta0 = vi.getMetadata();
        Long last = toLong(meta0 == null ? null : meta0.get("lastSyncLastPostId"));
        if (last == null) last = toLong(meta0 == null ? null : meta0.get("lastBuildLastPostId"));
        if (last == null) last = 0L;

        Long from = last > 0 ? last + 1 : null;
        RagPostsBuildResponse resp = buildPosts(vectorIndexId, boardId, from, postBatchSize, chunkMaxChars, chunkOverlapChars, false, embeddingModelOverride, expectedEmbeddingDims);

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

    private void deleteByQuery(String indexName, String body) {
        if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("indexName is blank");
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
