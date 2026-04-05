package com.example.EnterpriseRagCommunity.service.moderation.es;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesReindexResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesSyncResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiEmbeddingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.ModerationSamplesAutoSyncConfigService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ModerationSamplesSyncService {

    private static final Logger log = LoggerFactory.getLogger(ModerationSamplesSyncService.class);

    private final ElasticsearchTemplate template;
    private final ModerationSamplesIndexService indexService;
    private final ModerationSamplesIndexConfigService indexConfigService;
    private final ModerationSamplesRepository samplesRepository;
    private final AiEmbeddingService embeddingService;
    private final LlmGateway llmGateway;
    private final LlmRoutingService llmRoutingService;
    private final ModerationSimilarityConfigRepository configRepository;
    private final ModerationSamplesAutoSyncConfigService autoSyncConfigService;

    public ModerationSamplesSyncResult upsertById(Long id) {
        ModerationSamplesSyncResult r = new ModerationSamplesSyncResult();
        r.setId(id);
        r.setAction("upsert");

        if (id == null || id <= 0) {
            r.setSuccess(false);
            r.setMessage("id is required");
            return r;
        }

        ModerationSamplesEntity e;
        try {
            e = samplesRepository.findById(id).orElse(null);
        } catch (Exception ex) {
            r.setSuccess(false);
            r.setMessage("load sample failed: " + ex.getMessage());
            return r;
        }
        if (e == null) {
            r.setSuccess(false);
            r.setMessage("sample not found: id=" + id);
            return r;
        }

        try {
            // embed by normalized_text for best determinism
            ModerationSimilarityConfigEntity cfg = loadConfigOrNull();
            String input0 = e.getNormalizedText() != null ? e.getNormalizedText() : "";

            Integer maxInputChars0 = cfg == null ? null : cfg.getMaxInputChars();
            int maxInputChars = Math.max(0, maxInputChars0 == null ? 0 : maxInputChars0);
            String input = truncateByChars(input0, maxInputChars);

            String modelToUse = toNonBlank(cfg == null ? null : cfg.getEmbeddingModel());
            if (modelToUse == null) modelToUse = toNonBlank(indexConfigService.getEmbeddingModelOrDefault());
            if (modelToUse != null) {
                String candidateModel = modelToUse;
                boolean enabled = llmRoutingService.listEnabledTargets(LlmQueueTaskType.SIMILARITY_EMBEDDING)
                        .stream()
                        .anyMatch(t -> t != null && candidateModel.equals(t.modelName()));
                if (!enabled) modelToUse = null;
            }

            AiEmbeddingService.EmbeddingResult er;
            if (modelToUse == null) {
                er = llmGateway.embedOnceRouted(LlmQueueTaskType.SIMILARITY_EMBEDDING, null, null, input);
            } else {
                er = embeddingService.embedOnceForTask(input, modelToUse, null, LlmQueueTaskType.SIMILARITY_EMBEDDING);
            }
            float[] vec = er == null ? null : er.vector();
            if (vec == null || vec.length == 0) {
                r.setSuccess(false);
                r.setMessage("Embedding returned empty vector for sample id=" + id);
                return r;
            }

            Integer cfgDims0 = cfg == null ? null : cfg.getEmbeddingDims();
            int configuredDims = cfgDims0 != null ? cfgDims0 : indexConfigService.getEmbeddingDimsOrDefault();
            int inferredDims = vec.length;
            if (configuredDims > 0 && configuredDims != inferredDims) {
                r.setSuccess(false);
                r.setMessage(
                        "Embedding dims mismatch: configuredDims=" + configuredDims
                                + " but embedding returned=" + inferredDims + " (sample id=" + id + ")");
                return r;
            }

            int dimsToUse = configuredDims > 0 ? configuredDims : inferredDims;
            indexService.ensureIndex(dimsToUse);

            Document doc = toEsDoc(e, vec);
            IndexCoordinates idx = IndexCoordinates.of(indexService.getIndexName());
            template.save(doc, idx);

            // Refresh for immediate manual check UX
            try {
                template.indexOps(idx).refresh();
            } catch (Exception refreshEx) {
                log.debug("ES refresh failed after upsert id={}, err={}", id, refreshEx.getMessage());
            }

            r.setSuccess(true);
            r.setMessage("upserted into ES index=" + indexService.getIndexName());
            return r;
        } catch (Exception ex) {
            r.setSuccess(false);
            r.setMessage(ex.getMessage());
            return r;
        }
    }

    private ModerationSimilarityConfigEntity loadConfigOrNull() {
        try {
            return configRepository.findAll().stream().findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String truncateByChars(String s, int maxChars) {
        if (s == null) return "";
        if (maxChars <= 0) return s;
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars);
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    public ModerationSamplesSyncResult deleteById(Long id) {
        ModerationSamplesSyncResult r = new ModerationSamplesSyncResult();
        r.setId(id);
        r.setAction("delete");

        if (id == null || id <= 0) {
            r.setSuccess(false);
            r.setMessage("id is required");
            return r;
        }

        IndexCoordinates idx = IndexCoordinates.of(indexService.getIndexName());
        try {
            template.delete(String.valueOf(id), idx);
            try {
                template.indexOps(idx).refresh();
            } catch (Exception refreshEx) {
                log.debug("ES refresh failed after delete id={}, err={}", id, refreshEx.getMessage());
            }
            r.setSuccess(true);
            r.setMessage("deleted from ES (id=" + id + ")");
            return r;
        } catch (Exception ex) {
            // Delete is best-effort; provide message for manual fix.
            throw new IllegalStateException("ES delete failed for id=" + id + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Incremental sync MySQL -> ES without clearing index or orphan cleanup.
     */
    public ModerationSamplesReindexResponse syncIncremental(Boolean onlyEnabled, Integer batchSize, Long fromId) {
        Long effectiveFromId = fromId;
        if (effectiveFromId == null || effectiveFromId <= 0) {
            long cursor = autoSyncConfigService.getIncrementalSyncCursorLastIdOrDefault(0);
            if (cursor > 0) effectiveFromId = cursor;
        }
        ModerationSamplesReindexResponse resp = syncByCursor(onlyEnabled, batchSize, effectiveFromId, false, false);
        autoSyncConfigService.markIncrementalSyncFinished(resp.getLastId());
        return resp;
    }

    public ModerationSamplesReindexResponse reindexAll(Boolean onlyEnabled, Integer batchSize, Long fromId) {
        boolean doClear = (fromId == null || fromId <= 0);
        ModerationSamplesReindexResponse resp = syncByCursor(onlyEnabled, batchSize, fromId, doClear, doClear);
        autoSyncConfigService.updateIncrementalSyncCursorLastId(resp.getLastId() == null ? 0 : resp.getLastId());
        return resp;
    }

    private ModerationSamplesReindexResponse syncByCursor(Boolean onlyEnabled, Integer batchSize, Long fromId,
                                                         boolean clearIndexBefore, boolean cleanupOrphansAfter) {
        boolean onlyEn = onlyEnabled != null ? onlyEnabled : Boolean.TRUE;
        int bs = batchSize != null ? batchSize : 200;
        bs = Math.clamp(bs, 1, 1000);

        long total = 0;
        long success = 0;
        long failed = 0;
        List<Long> failedIds = new ArrayList<>();

        long startId = (fromId != null && fromId > 0) ? fromId : 0L;

        ModerationSamplesReindexResponse resp = new ModerationSamplesReindexResponse();
        resp.setFromId(startId == 0 ? null : startId);
        resp.setBatchSize(bs);
        resp.setOnlyEnabled(onlyEn);
        resp.setCleared(Boolean.FALSE);
        resp.setOrphanDeleted(0L);
        resp.setOrphanFailed(0L);

        if (clearIndexBefore) {
            try {
                clearAndRecreateIndex();
                resp.setCleared(Boolean.TRUE);
            } catch (Exception ex) {
                resp.setCleared(Boolean.FALSE);
                resp.setClearError(ex.getMessage());
                // If clear failed, abort to avoid mixed state.
                resp.setTotal(0);
                resp.setSuccess(0);
                resp.setFailed(0);
                return resp;
            }
        }

        // Iterate with paging by id asc; for large tables, cursor is more robust.
        long lastId = startId;
        while (true) {
            final long cursorId = lastId;
            Pageable pageable = PageRequest.of(0, bs, Sort.by(Sort.Direction.ASC, "id"));
            Page<ModerationSamplesEntity> page = samplesRepository.findAll((root, _query, cb) -> {
                var p = cb.conjunction();
                if (onlyEn) p = cb.and(p, cb.equal(root.get("enabled"), true));
                if (cursorId > 0) p = cb.and(p, cb.greaterThan(root.get("id"), cursorId));
                return p;
            }, pageable);

            List<ModerationSamplesEntity> items = page.getContent();
            if (items.isEmpty()) break;

            for (ModerationSamplesEntity e : items) {
                total++;
                ModerationSamplesSyncResult r = upsertById(e.getId());
                if (r.isSuccess()) {
                    success++;
                } else {
                    failed++;
                    if (failedIds.size() < 50 && e.getId() != null) failedIds.add(e.getId());
                    log.warn("Sync sample id={} failed: {}", e.getId(), r.getMessage());
                }
                lastId = Math.max(lastId, e.getId() == null ? lastId : e.getId());
            }
        }

        if (cleanupOrphansAfter) {
            OrphanCleanupStat st = cleanupOrphans();
            resp.setOrphanDeleted(st.deleted);
            resp.setOrphanFailed(st.failed);
            resp.setOrphanFailedIds(st.failedIds);
        }

        resp.setTotal(total);
        resp.setSuccess(success);
        resp.setFailed(failed);
        resp.setFailedIds(failedIds);
        resp.setLastId(lastId <= 0 ? null : lastId);
        return resp;
    }

    private void clearAndRecreateIndex() {
        IndexCoordinates idx = IndexCoordinates.of(indexService.getIndexName());
        var ops = template.indexOps(idx);
        if (ops.exists()) {
            boolean ok = ops.delete();
            if (!ok) {
                throw new IllegalStateException("failed to delete ES index=" + indexService.getIndexName());
            }
        }
        // recreate using configured dims (or 0). Actual dims will be validated on first upsert.
        indexService.ensureIndex();
        try {
            ops.refresh();
        } catch (Exception ignore) {
        }
    }

    private record OrphanCleanupStat(long deleted, long failed, List<Long> failedIds) {
    }

    /**
     * Delete ES docs whose id no longer exists in MySQL.
     * Best-effort: failures are collected in the response.
     */
    private OrphanCleanupStat cleanupOrphans() {
        long deleted = 0;
        long failed = 0;
        List<Long> failedIds = new ArrayList<>();

        IndexCoordinates idx = IndexCoordinates.of(indexService.getIndexName());
        if (!template.indexOps(idx).exists()) {
            return new OrphanCleanupStat(0, 0, List.of());
        }

        // NOTE: This is a pragmatic implementation: page through ES by id field.
        // We only need _id to check existence in MySQL.
        int pageSize = 200;
        long lastId = 0L;
        while (true) {
            // query: id > lastId, sort by id asc
            org.springframework.data.elasticsearch.core.query.CriteriaQuery q =
                    new org.springframework.data.elasticsearch.core.query.CriteriaQuery(
                            new org.springframework.data.elasticsearch.core.query.Criteria("id").greaterThan(lastId));
            q.setPageable(PageRequest.of(0, pageSize));
            q.addSort(Sort.by(Sort.Direction.ASC, "id"));
            q.addFields("id");

            var hits = template.search(q, Document.class, idx);
            if (hits.isEmpty()) break;

            long maxSeen = lastId;
            for (var h : hits) {
                Document doc = h.getContent();
                Object idObj = doc.get("id");
                Long id;
                try {
                    id = idObj == null ? null : Long.valueOf(String.valueOf(idObj));
                } catch (Exception ex) {
                    continue;
                }
                if (id == null || id <= 0) continue;
                maxSeen = Math.max(maxSeen, id);

                boolean exists;
                try {
                    exists = samplesRepository.existsById(id);
                } catch (Exception ex) {
                    exists = true; // don't delete if we can't be sure
                }

                if (!exists) {
                    try {
                        template.delete(String.valueOf(id), idx);
                        deleted++;
                    } catch (Exception ex) {
                        failed++;
                        if (failedIds.size() < 50) failedIds.add(id);
                        log.warn("Orphan delete failed id={}, err={}", id, ex.getMessage());
                    }
                }
            }

            if (maxSeen <= lastId) break;
            lastId = maxSeen;
            if (hits.getSearchHits().size() < pageSize) break;
        }

        try {
            template.indexOps(idx).refresh();
        } catch (Exception ignore) {
        }

        return new OrphanCleanupStat(deleted, failed, failedIds);
    }

    private static Document toEsDoc(ModerationSamplesEntity e, float[] embedding) {
        Document d = Document.create();
        if (e.getId() != null) {
            d.setId(String.valueOf(e.getId()));
            d.put("id", e.getId());
        }
        if (e.getCategory() != null) d.put("category", e.getCategory().name());
        if (e.getSource() != null) d.put("source", e.getSource().name());
        if (e.getRiskLevel() != null) d.put("risk_level", e.getRiskLevel());
        if (e.getEnabled() != null) d.put("enabled", e.getEnabled());
        if (e.getTextHash() != null) d.put("text_hash", e.getTextHash());
        if (e.getLabels() != null) d.put("labels", e.getLabels());
        if (e.getRawText() != null) d.put("raw_text", e.getRawText());
        if (e.getNormalizedText() != null) d.put("normalized_text", e.getNormalizedText());

        if (e.getCreatedAt() != null) d.put("created_at", Date.from(e.getCreatedAt().toInstant(ZoneOffset.UTC)));
        if (e.getUpdatedAt() != null) d.put("updated_at", Date.from(e.getUpdatedAt().toInstant(ZoneOffset.UTC)));

        // embedding: ES dense_vector expects array of numbers
        if (embedding != null && embedding.length > 0) {
            List<Float> vec = new ArrayList<>(embedding.length);
            for (float v : embedding) vec.add(v);
            d.put("embedding", vec);
        }
        return d;
    }
}
