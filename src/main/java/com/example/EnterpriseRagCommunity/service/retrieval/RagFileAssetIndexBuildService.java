package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.dto.retrieval.RagFilesBuildResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSampleTextUtils;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagFileAssetIndexBuildService {

    private static final Logger log = LoggerFactory.getLogger(RagFileAssetIndexBuildService.class);

    private final VectorIndicesRepository vectorIndicesRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final AiEmbeddingService embeddingService;
    private final LlmRoutingService llmRoutingService;
    private final RagFileAssetsIndexService indexService;
    private final ElasticsearchTemplate esTemplate;
    private final SystemConfigurationService systemConfigurationService;

    @SuppressWarnings("DataFlowIssue")
    @Transactional
    public RagFilesBuildResponse buildFiles(Long vectorIndexId,
                                           Long fromFileAssetId,
                                           Integer fileBatchSize,
                                           Integer chunkMaxChars,
                                           Integer chunkOverlapChars,
                                           Boolean clearIndex,
                                           String embeddingModelOverride,
                                           String embeddingProviderId,
                                           Integer expectedEmbeddingDims) {
        long started = System.currentTimeMillis();
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? indexService.defaultIndexName()
                : vi.getCollectionName().trim();

        RagFilesBuildResponse resp = new RagFilesBuildResponse();
        resp.setFromFileAssetId(fromFileAssetId);
        resp.setFileBatchSize(fileBatchSize);
        resp.setChunkMaxChars(chunkMaxChars);
        resp.setChunkOverlapChars(chunkOverlapChars);

        int ps = fileBatchSize == null || fileBatchSize < 1 ? 100 : Math.min(1000, fileBatchSize);
        RagChunkingSupport.ChunkingParams chunking = RagChunkingSupport.resolve(chunkMaxChars, chunkOverlapChars);
        int maxChars = chunking.maxChars();
        int overlap = chunking.overlap();

        Map<String, Object> meta0ForDefaults = vi.getMetadata();
        RagEmbeddingBuildSupport.ResolvedEmbeddingTarget embeddingTarget =
                RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                        llmRoutingService,
                        embeddingModelOverride,
                        embeddingProviderId,
                        meta0ForDefaults,
                        true,
                        null
                );
        String modelToUse = embeddingTarget.model();
        String providerToUse = embeddingTarget.providerId();

        Integer configuredDims = RagEmbeddingBuildSupport.resolveConfiguredDims(expectedEmbeddingDims, vi.getDim());

        vi.setStatus(VectorIndexStatus.BUILDING);
        vectorIndicesRepository.save(vi);

        boolean clearRequested = Boolean.TRUE.equals(clearIndex);
        boolean clearPending = clearRequested;
        Boolean cleared = null;
        String clearError = null;
        boolean clearedOk = false;

        long totalFiles = 0;
        long totalChunks = 0;
        long success = 0;
        long failed = 0;
        List<String> failedDocIds = new ArrayList<>();
        List<RagFilesBuildResponse.FailedDoc> failedDocs = new ArrayList<>();
        Long lastFileAssetId = null;

        Integer dimsToUse = null;
        boolean ensured = false;

        boolean mayRewriteExisting = fromFileAssetId == null || fromFileAssetId <= 0;
        boolean cleanupPerFile = mayRewriteExisting && !clearRequested;

        long cursor = fromFileAssetId == null ? 0L : Math.max(0L, fromFileAssetId);
        while (true) {
            Page<FileAssetExtractionsEntity> batch = fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(
                    FileAssetExtractionStatus.READY,
                    cursor,
                    PageRequest.of(0, ps, Sort.by(Sort.Direction.ASC, "fileAssetId"))
            );
            if (batch == null || batch.isEmpty()) break;

            List<Long> ids = batch.getContent().stream().map(FileAssetExtractionsEntity::getFileAssetId).filter(x -> x != null && x > 0).toList();
            Map<Long, FileAssetsEntity> faById = new HashMap<>();
            if (!ids.isEmpty()) {
                for (FileAssetsEntity fa : fileAssetsRepository.findAllById(ids)) {
                    if (fa != null && fa.getId() != null) faById.put(fa.getId(), fa);
                }
            }

            Map<Long, List<Long>> postIdsByFileAssetId = new HashMap<>();
            if (!ids.isEmpty()) {
                for (PostAttachmentsEntity pa : postAttachmentsRepository.findByFileAssetIdIn(ids)) {
                    if (pa == null) continue;
                    Long fid = pa.getFileAssetId();
                    Long pid = pa.getPostId();
                    if (fid == null || pid == null) continue;
                    postIdsByFileAssetId.computeIfAbsent(fid, k -> new ArrayList<>()).add(pid);
                }
            }

            for (FileAssetExtractionsEntity ex : batch.getContent()) {
                if (ex == null || ex.getFileAssetId() == null) continue;
                Long fileAssetId = ex.getFileAssetId();
                lastFileAssetId = fileAssetId;
                cursor = Math.max(cursor, fileAssetId);

                FileAssetsEntity fa = faById.get(fileAssetId);
                if (fa == null) continue;

                String raw0 = ex.getExtractedText();
                if (raw0 != null && !raw0.isBlank()) {
                    raw0 = raw0.replaceAll("\\[\\[IMAGE_\\d+]]", " ");
                }
                String raw = ModerationSampleTextUtils.normalize(raw0);
                if (raw == null || raw.isBlank()) continue;
                totalFiles++;

                if (cleanupPerFile) {
                    try {
                        deleteByQuery(indexName, "{\"query\":{\"term\":{\"file_asset_id\":" + fileAssetId + "}}}");
                    } catch (Exception ex2) {
                        log.warn("RAG cleanup existing docs failed. fileAssetId={}, err={}", fileAssetId, ex2.getMessage());
                    }
                }

                List<String> chunks = buildChunksForFileAsset(fa, raw, maxChars, overlap);
                if (chunks.isEmpty()) continue;

                List<Long> postIds = postIdsByFileAssetId.get(fileAssetId);
                List<Long> uniquePostIds = null;
                if (postIds != null && !postIds.isEmpty()) {
                    Set<Long> uniq = new LinkedHashSet<>();
                    for (Long pid : postIds) {
                        if (pid != null && pid > 0) uniq.add(pid);
                    }
                    if (!uniq.isEmpty()) uniquePostIds = new ArrayList<>(uniq);
                }

                for (int ci = 0; ci < chunks.size(); ci++) {
                    String chunk = chunks.get(ci);
                    if (chunk == null || chunk.isBlank()) continue;
                    totalChunks++;

                    String docId = "file_asset_" + fileAssetId + "_chunk_" + ci;
                    String contentHash = ModerationSampleTextUtils.sha256Hex(chunk);
                    try {
                        AiEmbeddingService.EmbeddingResult emb = embeddingService.embedOnceForTask(chunk, modelToUse, providerToUse, LlmQueueTaskType.POST_EMBEDDING);
                        if (emb == null || emb.vector() == null) throw new IllegalStateException("embedding is null");

                        if (!ensured) {
                            int inferredDims = emb.dims();
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
                                } catch (Exception ex2) {
                                    try {
                                        deleteIndexViaHttp(indexName);
                                        cleared = true;
                                        clearedOk = true;
                                        clearPending = false;
                                    } catch (Exception fallback) {
                                        cleared = false;
                                        clearError = "delete-index failed: " + ex2.getMessage() + " | fallback http-delete-index failed: " + fallback.getMessage();
                                    }
                                }
                                if (!clearedOk) {
                                    throw new IllegalStateException("清空 ES 索引失败（需要先删除索引才能全量重建）: " + (clearError == null ? "" : clearError));
                                }
                            }
                            indexService.ensureIndex(indexName, dimsToUse, true);
                            ensured = true;
                        }

                        Document d = buildFileAssetDocument(
                                docId,
                                fileAssetId,
                                fa,
                                ex,
                                uniquePostIds,
                                ci,
                                contentHash,
                                chunk,
                                emb.vector()
                        );
                        esTemplate.save(d, IndexCoordinates.of(indexName));
                        success++;
                    } catch (Exception ex2) {
                        failed++;
                        if (failedDocIds.size() < 50) failedDocIds.add(docId);
                        if (failedDocs.size() < 50) {
                            RagFilesBuildResponse.FailedDoc fd = new RagFilesBuildResponse.FailedDoc();
                            fd.setDocId(docId);
                            fd.setError(summarizeException(ex2));
                            failedDocs.add(fd);
                        }
                        log.warn("RAG file chunk upsert failed. docId={}, err={}", docId, summarizeException(ex2), ex2);
                    }
                }
            }
        }

        Map<String, Object> meta = VectorIndexMetadataSupport.prepareBuildMetadata(
                vi,
                vectorIndicesRepository,
                dimsToUse,
                failed,
                indexName,
                "FILE_ASSET"
        );
        meta.put("lastBuildAt", LocalDateTime.now().toString());
        meta.put("lastBuildTotalFiles", totalFiles);
        meta.put("lastBuildTotalChunks", totalChunks);
        meta.put("lastBuildSuccessChunks", success);
        meta.put("lastBuildFailedChunks", failed);
        meta.put("lastBuildFileBatchSize", ps);
        if (fromFileAssetId != null) meta.put("lastBuildFromFileAssetId", fromFileAssetId);
        if (lastFileAssetId != null) meta.put("lastBuildLastFileAssetId", lastFileAssetId);
        VectorIndexMetadataSupport.putBuildEmbeddingMetadata(meta, maxChars, overlap, dimsToUse, modelToUse, providerToUse, cleared, clearError);
        vi.setMetadata(meta);
        vectorIndicesRepository.save(vi);

        resp.setTotalFiles(totalFiles);
        resp.setTotalChunks(totalChunks);
        resp.setSuccessChunks(success);
        resp.setFailedChunks(failed);
        resp.setFailedDocIds(failedDocIds.isEmpty() ? null : failedDocIds);
        resp.setFailedDocs(failedDocs.isEmpty() ? null : failedDocs);
        resp.setLastFileAssetId(lastFileAssetId);
        resp.setCleared(cleared);
        resp.setClearError(clearError);
        resp.setEmbeddingDims(dimsToUse);
        resp.setEmbeddingModel(modelToUse);
        resp.setEmbeddingProviderId(providerToUse);
        resp.setTookMs(System.currentTimeMillis() - started);
        return resp;
    }

    @Transactional
    public RagFilesBuildResponse rebuildFiles(Long vectorIndexId,
                                             Integer fileBatchSize,
                                             Integer chunkMaxChars,
                                             Integer chunkOverlapChars,
                                             String embeddingModelOverride,
                                             String embeddingProviderId,
                                             Integer expectedEmbeddingDims) {
        RagFilesBuildResponse resp = buildFiles(vectorIndexId, null, fileBatchSize, chunkMaxChars, chunkOverlapChars, true, embeddingModelOverride, embeddingProviderId, expectedEmbeddingDims);
        touchMetadata(vectorIndexId, meta -> {
            meta.put("lastRebuildAt", LocalDateTime.now().toString());
            meta.put("lastRebuildTookMs", resp.getTookMs());
            meta.put("lastRebuildTotalFiles", resp.getTotalFiles());
            meta.put("lastRebuildLastFileAssetId", resp.getLastFileAssetId());
        });
        return resp;
    }

    @Transactional
    public RagFilesBuildResponse syncFilesIncremental(Long vectorIndexId,
                                                      Integer fileBatchSize,
                                                      Integer chunkMaxChars,
                                                      Integer chunkOverlapChars,
                                                      String embeddingModelOverride,
                                                      String embeddingProviderId,
                                                      Integer expectedEmbeddingDims) {
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        Map<String, Object> meta0 = vi.getMetadata();
        Long last = toLong(meta0 == null ? null : meta0.get("lastSyncLastFileAssetId"));
        if (last == null) last = toLong(meta0 == null ? null : meta0.get("lastBuildLastFileAssetId"));
        if (last == null) last = 0L;

        Long from = last > 0 ? last : null;
        RagFilesBuildResponse resp = buildFiles(vectorIndexId, from, fileBatchSize, chunkMaxChars, chunkOverlapChars, false, embeddingModelOverride, embeddingProviderId, expectedEmbeddingDims);

        Long newLast = resp.getLastFileAssetId() == null ? last : resp.getLastFileAssetId();
        boolean noop = resp.getTotalFiles() == 0;
        touchMetadata(vectorIndexId, meta -> {
            meta.put("lastSyncAt", LocalDateTime.now().toString());
            meta.put("lastSyncFromFileAssetId", from);
            meta.put("lastSyncLastFileAssetId", newLast);
            meta.put("lastSyncTookMs", resp.getTookMs());
            meta.put("lastSyncTotalFiles", resp.getTotalFiles());
            meta.put("lastSyncNoop", noop);
        });
        return resp;
    }

    public void syncSingleFileAsset(Long vectorIndexId, Long fileAssetId) {
        syncSingleFileAsset(vectorIndexId, fileAssetId, null, null, null);
    }

    public void syncSingleFileAsset(Long vectorIndexId,
                                    Long fileAssetId,
                                    Integer chunkMaxChars,
                                    Integer chunkOverlapChars,
                                    Integer expectedEmbeddingDims) {
        if (vectorIndexId == null || fileAssetId == null) return;

        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId)
                .orElseThrow(() -> new IllegalArgumentException("vector index not found: " + vectorIndexId));

        String indexName = (vi.getCollectionName() == null || vi.getCollectionName().isBlank())
                ? indexService.defaultIndexName()
                : vi.getCollectionName().trim();

        try {
            deleteByQuery(indexName, "{\"query\":{\"term\":{\"file_asset_id\":" + fileAssetId + "}}}");
        } catch (Exception ex) {
            log.warn("RAG delete stale file docs failed. vectorIndexId={}, fileAssetId={}, err={}", vectorIndexId, fileAssetId, ex.getMessage());
        }

        FileAssetsEntity fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
        FileAssetExtractionsEntity ex = fileAssetExtractionsRepository.findById(fileAssetId).orElse(null);
        if (fa == null || ex == null || ex.getExtractStatus() != FileAssetExtractionStatus.READY) {
            return;
        }

        RagChunkingSupport.ChunkingParams chunking = RagChunkingSupport.resolve(chunkMaxChars, chunkOverlapChars);
        int maxChars = chunking.maxChars();
        int overlap = chunking.overlap();

        Map<String, Object> meta0ForDefaults = vi.getMetadata();
        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));

        String modelToUse;
        String providerToUse = null;
        if (fixedProviderId != null) {
            providerToUse = fixedProviderId;
        }

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

        Integer configuredDims = RagEmbeddingBuildSupport.resolveConfiguredDims(expectedEmbeddingDims, vi.getDim());

        String raw = ModerationSampleTextUtils.normalize(ex.getExtractedText());
        if (raw.isBlank()) return;

        List<String> chunks = buildChunksForFileAsset(fa, raw, maxChars, overlap);
        if (chunks.isEmpty()) return;

        List<PostAttachmentsEntity> pa = postAttachmentsRepository.findByFileAssetIdIn(List.of(fileAssetId));
        List<Long> uniquePostIds = null;
        if (pa != null && !pa.isEmpty()) {
            Set<Long> uniq = new LinkedHashSet<>();
            for (PostAttachmentsEntity p : pa) {
                if (p == null) continue;
                Long pid = p.getPostId();
                if (pid != null && pid > 0) uniq.add(pid);
            }
            if (!uniq.isEmpty()) uniquePostIds = new ArrayList<>(uniq);
        }

        for (int ci = 0; ci < chunks.size(); ci++) {
            String chunk = chunks.get(ci);
            if (chunk == null || chunk.isBlank()) continue;
            String docId = "file_asset_" + fileAssetId + "_chunk_" + ci;
            String contentHash = ModerationSampleTextUtils.sha256Hex(chunk);
            AiEmbeddingService.EmbeddingResult emb;
            try {
                emb = embeddingService.embedOnceForTask(chunk, modelToUse, providerToUse, LlmQueueTaskType.POST_EMBEDDING);
            } catch (Exception e) {
                throw new IllegalStateException("Embedding failed: " + e.getMessage(), e);
            }
            if (emb == null || emb.vector() == null) throw new IllegalStateException("embedding is null");

            int inferredDims = emb.dims();
            validateEmbeddingDims(configuredDims, inferredDims);
            int dimsToUse = configuredDims != null ? configuredDims : inferredDims;
            indexService.ensureIndex(indexName, dimsToUse, true);

            Document d = buildFileAssetDocument(
                    docId,
                    fileAssetId,
                    fa,
                    ex,
                    uniquePostIds,
                    ci,
                    contentHash,
                    chunk,
                    emb.vector()
            );
            esTemplate.save(d, IndexCoordinates.of(indexName));
        }
    }

    private void touchMetadata(Long vectorIndexId, java.util.function.Consumer<Map<String, Object>> mut) {
        if (vectorIndexId == null || mut == null) return;
        VectorIndicesEntity vi = vectorIndicesRepository.findById(vectorIndexId).orElse(null);
        if (vi == null) return;
        Map<String, Object> meta0 = vi.getMetadata();
        Map<String, Object> meta = meta0 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(meta0);
        mut.accept(meta);
        vi.setMetadata(meta);
        vectorIndicesRepository.save(vi);
    }

    private static Long toLong(Object o) {
        return RagValueSupport.toLong(o);
    }

    private static String toNonBlankString(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isBlank() ? null : s;
    }

    private static List<String> buildChunksForFileAsset(FileAssetsEntity fileAsset, String raw, int maxChars, int overlap) {
        String header = buildFileAssetHeader(fileAsset);
        String text = header.isBlank() ? raw : (header + "\n" + raw);
        return splitWithOverlap(text, maxChars, overlap);
    }

    private static Document buildFileAssetDocument(
            String docId,
            Long fileAssetId,
            FileAssetsEntity fileAsset,
            FileAssetExtractionsEntity extraction,
            List<Long> uniquePostIds,
            int chunkIndex,
            String contentHash,
            String chunk,
            float[] vector
    ) {
        Document d = Document.create();
        d.setId(docId);
        d.put("id", docId);
        d.put("file_asset_id", fileAssetId);
        if (fileAsset != null && fileAsset.getOwner() != null && fileAsset.getOwner().getId() != null) {
            d.put("owner_user_id", fileAsset.getOwner().getId());
        }
        if (uniquePostIds != null) d.put("post_ids", uniquePostIds);
        d.put("chunk_index", chunkIndex);
        d.put("content_hash", contentHash);
        if (fileAsset != null && fileAsset.getOriginalName() != null && !fileAsset.getOriginalName().isBlank()) {
            d.put("file_name", fileAsset.getOriginalName().trim());
        }
        if (fileAsset != null && fileAsset.getMimeType() != null && !fileAsset.getMimeType().isBlank()) {
            d.put("mime_type", fileAsset.getMimeType().trim());
        }
        d.put("content_text", chunk);
        if (fileAsset != null && fileAsset.getCreatedAt() != null) {
            d.put("created_at", Date.from(fileAsset.getCreatedAt().toInstant(ZoneOffset.UTC)));
        }
        if (extraction != null && extraction.getUpdatedAt() != null) {
            d.put("updated_at", Date.from(extraction.getUpdatedAt().toInstant(ZoneOffset.UTC)));
        }
        if (vector != null && vector.length > 0) {
            List<Float> vec = new ArrayList<>(vector.length);
            for (float v : vector) vec.add(v);
            d.put("embedding", vec);
        }
        return d;
    }

    private static String buildFileAssetHeader(FileAssetsEntity fileAsset) {
        StringBuilder header = new StringBuilder();
        String fileName = fileAsset.getOriginalName();
        if (fileName != null && !fileName.isBlank()) {
            header.append("fileName: ").append(fileName.trim()).append('\n');
        }
        String mimeType = fileAsset.getMimeType();
        if (mimeType != null && !mimeType.isBlank()) {
            header.append("mimeType: ").append(mimeType.trim()).append('\n');
        }
        return header.toString().trim();
    }

    private static void validateEmbeddingDims(Integer configuredDims, Integer inferredDims) {
        if (configuredDims != null && configuredDims > 0 && inferredDims != null && inferredDims > 0 && !configuredDims.equals(inferredDims)) {
            throw new IllegalStateException("embedding dims mismatch: expected=" + configuredDims + ", got=" + inferredDims);
        }
    }

    private static List<String> splitWithOverlap(String text, int maxChars, int overlapChars) {
        return RagValueSupport.splitWithOverlap(text, maxChars, overlapChars);
    }

    private void deleteByQuery(String indexName, String jsonBody) {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            String idx = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
            URL url = java.net.URI.create(endpoint + "/" + idx + "/_delete_by_query?conflicts=proceed").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(20_000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return;
            String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("ES delete-by-query failed HTTP " + code + ": " + resp);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ES delete-by-query failed: " + e.getMessage(), e);
        }
    }

    private void deleteIndexViaHttp(String indexName) {
        String endpoint = ElasticsearchHttpSupport.resolveEndpoint(systemConfigurationService);

        try {
            String idx = URLEncoder.encode(indexName, StandardCharsets.UTF_8);
            URL url = java.net.URI.create(endpoint + "/" + idx).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(10_000);
            ElasticsearchHttpSupport.applyApiKey(conn, systemConfigurationService);
            int code = conn.getResponseCode();
            if (code == 404) return;
            if (code < 200 || code >= 300) {
                InputStream is = conn.getErrorStream();
                String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("ES delete index failed HTTP " + code + ": " + body);
            }
        } catch (Exception e) {
            throw new IllegalStateException("ES delete index failed: " + e.getMessage(), e);
        }
    }

    private static String summarizeException(Throwable t) {
        if (t == null) return "error";
        String m = t.getMessage();
        if (m == null || m.isBlank()) return t.getClass().getSimpleName();
        String s = m.trim();
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
