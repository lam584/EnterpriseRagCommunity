package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarityConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarHitsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarHitsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSimilarityConfigRepository;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSimilarityService;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleCreateRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleUpdateRequest;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSampleTextUtils;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesReindexResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesIndexStatusResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesSyncResult;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesAutoSyncConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesIndexService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.ModerationSamplesAutoSyncConfigService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/admin/moderation/embed")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminModerationEmbedController {

    private static final Logger log = LoggerFactory.getLogger(AdminModerationEmbedController.class);

    private final ModerationSimilarityService similarityService;
    private final ModerationSimilarityConfigRepository configRepository;
    private final ModerationSamplesIndexConfigService indexConfigService;
    private final ModerationSamplesRepository samplesRepository;
    private final ModerationSimilarHitsRepository hitsRepository;
    private final ModerationSamplesSyncService samplesSyncService;
    private final ModerationSamplesIndexService samplesIndexService;
    private final ModerationSamplesAutoSyncConfigService samplesAutoSyncConfigService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @PostMapping("/check")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<SimilarityCheckResponse> check(@RequestBody SimilarityCheckRequest req) {
        return ResponseEntity.ok(similarityService.check(req));
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSimilarityConfigEntity> getConfig() {
        ModerationSimilarityConfigEntity cfg = configRepository.findAll().stream().findFirst().orElseGet(() -> {
            ModerationSimilarityConfigEntity e = new ModerationSimilarityConfigEntity();
            e.setEnabled(true);
            e.setEmbeddingModel(null);
            e.setEmbeddingDims(indexConfigService.getEmbeddingDimsOrDefault());
            e.setMaxInputChars(0);
            e.setDefaultTopK(5);
            e.setDefaultNumCandidates(0);
            e.setUpdatedAt(LocalDateTime.now());
            return configRepository.save(e);
        });
        return ResponseEntity.ok(cfg);
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSimilarityConfigEntity> updateConfig(@RequestBody(required = false) ModerationSimilarityConfigEntity payload) {
        Boolean enabled = payload == null ? null : payload.getEnabled();
        if (enabled == null) throw new IllegalArgumentException("enabled 不能为空");

        ModerationSimilarityConfigEntity cfg = configRepository.findAll().stream().findFirst().orElseGet(() -> {
            ModerationSimilarityConfigEntity e = new ModerationSimilarityConfigEntity();
            e.setEnabled(true);
            e.setEmbeddingModel(null);
            e.setEmbeddingDims(indexConfigService.getEmbeddingDimsOrDefault());
            e.setMaxInputChars(0);
            e.setDefaultTopK(5);
            e.setDefaultNumCandidates(0);
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });

        Map<String, Object> before = summarizeConfig(cfg);
        cfg.setEnabled(enabled);
        if (payload != null) {
            if (payload.getEmbeddingModel() != null) {
                String m = payload.getEmbeddingModel().trim();
                cfg.setEmbeddingModel(m.isBlank() ? null : m);
            }
            if (payload.getEmbeddingDims() != null) cfg.setEmbeddingDims(Math.max(0, payload.getEmbeddingDims()));
            if (payload.getMaxInputChars() != null) cfg.setMaxInputChars(Math.max(0, payload.getMaxInputChars()));
            if (payload.getDefaultTopK() != null) cfg.setDefaultTopK(Math.max(1, Math.min(50, payload.getDefaultTopK())));
            if (payload.getDefaultNumCandidates() != null) cfg.setDefaultNumCandidates(Math.max(0, payload.getDefaultNumCandidates()));
        }
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg = configRepository.save(cfg);

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "CONFIG_CHANGE",
                "MODERATION_EMBED_CONFIG",
                cfg.getId(),
                AuditResult.SUCCESS,
                "更新嵌入相似检测配置",
                null,
                auditDiffBuilder.build(before, summarizeConfig(cfg))
        );
        return ResponseEntity.ok(cfg);
    }

    @GetMapping("/index-status")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesIndexStatusResponse> getIndexStatus() {
        ModerationSamplesIndexStatusResponse r = new ModerationSamplesIndexStatusResponse();
        r.setIndexName(samplesIndexService.getIndexName());
        boolean exists = samplesIndexService.indexExists();
        r.setExists(exists);
        Integer configured = configRepository.findAll().stream().findFirst().map(ModerationSimilarityConfigEntity::getEmbeddingDims).orElse(null);
        if (configured == null || configured <= 0) {
            configured = indexConfigService.getEmbeddingDimsOrDefault();
        }
        r.setEmbeddingDimsConfigured(configured);
        r.setLastIncrementalSyncAt(samplesAutoSyncConfigService.getLastIncrementalSyncAt().orElse(null));
        boolean available = true;
        String availabilityMessage = null;
        if (exists) {
            Integer mappingDims = samplesIndexService.getEmbeddingDimsInMapping();
            r.setEmbeddingDimsInMapping(mappingDims);
            r.setDocCount(samplesIndexService.countDocs());
            if (mappingDims == null || mappingDims <= 0) {
                available = false;
                availabilityMessage = "索引映射缺少 embedding 向量字段";
            } else {
                Integer configuredDims = r.getEmbeddingDimsConfigured();
                if (configuredDims != null && configuredDims > 0 && !configuredDims.equals(mappingDims)) {
                    available = false;
                    availabilityMessage = "embedding 维度不一致（配置 " + configuredDims + " / 映射 " + mappingDims + "）";
                }
            }
        } else {
            available = false;
            availabilityMessage = "索引不存在";
        }
        r.setAvailable(available);
        r.setAvailabilityMessage(availabilityMessage);
        return ResponseEntity.ok(r);
    }

    @GetMapping("/samples/auto-sync/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesAutoSyncConfigDTO> getSamplesAutoSyncConfig() {
        return ResponseEntity.ok(samplesAutoSyncConfigService.getConfig());
    }

    @PutMapping("/samples/auto-sync/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesAutoSyncConfigDTO> updateSamplesAutoSyncConfig(
            @RequestBody(required = false) ModerationSamplesAutoSyncConfigDTO payload
    ) {
        ModerationSamplesAutoSyncConfigDTO before = samplesAutoSyncConfigService.getConfig();
        ModerationSamplesAutoSyncConfigDTO after = samplesAutoSyncConfigService.updateConfig(payload);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "CONFIG_CHANGE",
                "MODERATION_EMBED_AUTO_SYNC_CONFIG",
                null,
                AuditResult.SUCCESS,
                "更新样本自动同步配置",
                null,
                auditDiffBuilder.build(before, after)
        );
        return ResponseEntity.ok(after);
    }

    @GetMapping("/samples")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public Page<ModerationSamplesEntity> listSamples(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam(value = "category", required = false) ModerationSamplesEntity.Category category,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "textHash", required = false) String textHash,
            @RequestParam(value = "createdFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1),
                Math.min(Math.max(pageSize, 1), 200),
                Sort.by(Sort.Direction.DESC, "id")
        );

        Specification<ModerationSamplesEntity> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            if (id != null) p = cb.and(p, cb.equal(root.get("id"), id));
            if (category != null) p = cb.and(p, cb.equal(root.get("category"), category));
            if (enabled != null) p = cb.and(p, cb.equal(root.get("enabled"), enabled));
            if (textHash != null && !textHash.isBlank()) p = cb.and(p, cb.equal(root.get("textHash"), textHash.trim()));
            if (createdFrom != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            if (createdTo != null) p = cb.and(p, cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            return p;
        };

        return samplesRepository.findAll(spec, pageable);
    }

    @GetMapping("/samples/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSampleDTO> getSample(@PathVariable("id") Long id) {
        ModerationSamplesEntity e = samplesRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sample not found"));
        return ResponseEntity.ok(ModerationSampleDTO.fromEntity(e));
    }

    @PostMapping("/samples")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSampleDTO> createSample(@RequestBody(required = false) ModerationSampleCreateRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
        if (req.getCategory() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category is required");
        if (req.getRawText() == null || req.getRawText().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawText is required");

        LocalDateTime now = LocalDateTime.now();
        String normalized = ModerationSampleTextUtils.normalize(req.getRawText());
        if (normalized.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawText is empty after normalize");
        String hash = ModerationSampleTextUtils.sha256Hex(normalized);

        // Dedup by text_hash
        Optional<ModerationSamplesEntity> existing = samplesRepository.findByTextHash(hash);
        if (existing.isPresent()) {
            // Treat as conflict; client can choose update flow.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicated sample: textHash already exists (id=" + existing.get().getId() + ")");
        }

        ModerationSamplesEntity e = new ModerationSamplesEntity();
        e.setCategory(req.getCategory());
        e.setRefContentType(req.getRefContentType());
        e.setRefContentId(req.getRefContentId());
        e.setRawText(req.getRawText());
        e.setNormalizedText(normalized);
        e.setTextHash(hash);
        e.setRiskLevel(req.getRiskLevel() != null ? req.getRiskLevel() : 0);
        e.setLabels(req.getLabels());
        e.setSource(req.getSource() != null ? req.getSource() : ModerationSamplesEntity.Source.HUMAN);
        e.setEnabled(req.getEnabled() != null ? req.getEnabled() : Boolean.TRUE);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        try {
            e = samplesRepository.save(e);
        } catch (DataIntegrityViolationException dup) {
            // In case of race condition
            throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicated sample: textHash already exists");
        }

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_SAMPLE_CREATE",
                "MODERATION_SAMPLE",
                e.getId(),
                AuditResult.SUCCESS,
                "创建相似检测样本",
                null,
                auditDiffBuilder.build(Map.of(), summarizeSample(e))
        );

        ModerationSamplesSyncResult syncResult = null;
        try {
            syncResult = samplesSyncService.upsertById(e.getId());
        } catch (Exception ex) {
            log.warn("Sample saved but ES upsert failed. id={}, err={}", e.getId(), ex.getMessage());
        }

        if (syncResult != null) {
            return ResponseEntity.ok(ModerationSampleDTO.fromEntityWithEsSync(
                    e,
                    syncResult.isSuccess(),
                    syncResult.isSuccess() ? null : (syncResult.getMessage() == null ? "ES sync failed" : syncResult.getMessage())
            ));
        }
        // unknown
        return ResponseEntity.ok(ModerationSampleDTO.fromEntityWithEsSync(e, null, null));
    }

    @PutMapping("/samples/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSampleDTO> updateSample(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ModerationSampleUpdateRequest req
    ) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");

        ModerationSamplesEntity e = samplesRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sample not found"));
        Map<String, Object> before = summarizeSample(e);

        if (req.getCategory() != null) e.setCategory(req.getCategory());
        e.setRefContentType(req.getRefContentType());
        e.setRefContentId(req.getRefContentId());

        if (req.getRawText() != null) {
            if (req.getRawText().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawText cannot be blank");
            String normalized = ModerationSampleTextUtils.normalize(req.getRawText());
            if (normalized.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rawText is empty after normalize");
            String hash = ModerationSampleTextUtils.sha256Hex(normalized);

            // If hash changed, ensure uniqueness
            if (!hash.equals(e.getTextHash())) {
                Optional<ModerationSamplesEntity> exists = samplesRepository.findByTextHash(hash);
                if (exists.isPresent() && !exists.get().getId().equals(e.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicated sample: textHash already exists (id=" + exists.get().getId() + ")");
                }
                e.setTextHash(hash);
            }

            e.setRawText(req.getRawText());
            e.setNormalizedText(normalized);
        }

        if (req.getRiskLevel() != null) e.setRiskLevel(req.getRiskLevel());
        if (req.getLabels() != null) e.setLabels(req.getLabels());
        if (req.getSource() != null) e.setSource(req.getSource());
        if (req.getEnabled() != null) e.setEnabled(req.getEnabled());

        e.setUpdatedAt(LocalDateTime.now());

        try {
            e = samplesRepository.save(e);
        } catch (DataIntegrityViolationException dup) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicated sample: textHash already exists");
        }

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_SAMPLE_UPDATE",
                "MODERATION_SAMPLE",
                e.getId(),
                AuditResult.SUCCESS,
                "更新相似检测样本",
                null,
                auditDiffBuilder.build(before, summarizeSample(e))
        );

        ModerationSamplesSyncResult syncResult = null;
        try {
            syncResult = samplesSyncService.upsertById(e.getId());
        } catch (Exception ex) {
            log.warn("Sample updated but ES upsert failed. id={}, err={}", e.getId(), ex.getMessage());
        }

        if (syncResult != null) {
            return ResponseEntity.ok(ModerationSampleDTO.fromEntityWithEsSync(
                    e,
                    syncResult.isSuccess(),
                    syncResult.isSuccess() ? null : (syncResult.getMessage() == null ? "ES sync failed" : syncResult.getMessage())
            ));
        }
        return ResponseEntity.ok(ModerationSampleDTO.fromEntityWithEsSync(e, null, null));
    }

    @DeleteMapping("/samples/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<Void> deleteSample(@PathVariable("id") Long id) {
        ModerationSamplesEntity before = samplesRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "sample not found"));
        samplesRepository.deleteById(id);

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_SAMPLE_DELETE",
                "MODERATION_SAMPLE",
                id,
                AuditResult.SUCCESS,
                "删除相似检测样本",
                null,
                auditDiffBuilder.build(summarizeSample(before), Map.of())
        );

        // Best-effort ES delete
        try {
            samplesSyncService.deleteById(id);
        } catch (Exception ex) {
            log.warn("Sample deleted from MySQL but ES delete failed. id={}, err={}", id, ex.getMessage());
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Reindex moderation_samples (MySQL) -> ES.
     * This fixes the common case where samples were added to MySQL but ES wasn't synced.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesReindexResponse> reindex(
            @RequestParam(value = "onlyEnabled", required = false) Boolean onlyEnabled,
            @RequestParam(value = "batchSize", required = false) Integer batchSize,
            @RequestParam(value = "fromId", required = false) Long fromId
    ) {
        ModerationSamplesReindexResponse res = samplesSyncService.reindexAll(onlyEnabled, batchSize, fromId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("onlyEnabled", onlyEnabled);
        details.put("batchSize", batchSize);
        details.put("fromId", fromId);
        if (res != null) {
            details.put("total", res.getTotal());
            details.put("success", res.getSuccess());
            details.put("failed", res.getFailed());
            details.put("lastId", res.getLastId());
            details.put("cleared", res.getCleared());
            details.put("clearError", res.getClearError());
        }
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_SAMPLES_REINDEX",
                "MODERATION_SAMPLES_INDEX",
                null,
                AuditResult.SUCCESS,
                "重建样本索引",
                null,
                details
        );
        return ResponseEntity.ok(res);
    }

    /**
     * Optional helper: manually upsert one sample to ES.
     */
    @PostMapping("/samples/{id}/sync")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesSyncResult> syncOne(@PathVariable("id") Long id) {
        ModerationSamplesSyncResult res = samplesSyncService.upsertById(id);
        Map<String, Object> details = new LinkedHashMap<>();
        if (res != null) {
            details.put("success", res.isSuccess());
            details.put("message", res.getMessage());
        }
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_SAMPLE_SYNC",
                "MODERATION_SAMPLE",
                id,
                res != null && res.isSuccess() ? AuditResult.SUCCESS : AuditResult.FAIL,
                "同步样本到索引",
                null,
                details
        );
        return ResponseEntity.ok(res);
    }

    @GetMapping("/hits")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public Page<ModerationSimilarHitsEntity> listHits(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "contentType", required = false) ContentType contentType,
            @RequestParam(value = "contentId", required = false) Long contentId,
            @RequestParam(value = "candidateId", required = false) Long candidateId,
            @RequestParam(value = "maxDistance", required = false) Double maxDistance,
            @RequestParam(value = "matchedFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime matchedFrom,
            @RequestParam(value = "matchedTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime matchedTo
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1),
                Math.min(Math.max(pageSize, 1), 200),
                Sort.by(Sort.Direction.DESC, "matchedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Specification<ModerationSimilarHitsEntity> spec = (root, query, cb) -> {
            var p = cb.conjunction();
            if (contentType != null) p = cb.and(p, cb.equal(root.get("contentType"), contentType));
            if (contentId != null) p = cb.and(p, cb.equal(root.get("contentId"), contentId));
            if (candidateId != null) p = cb.and(p, cb.equal(root.get("candidateId"), candidateId));
            if (maxDistance != null) p = cb.and(p, cb.lessThanOrEqualTo(root.get("distance"), maxDistance));
            if (matchedFrom != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("matchedAt"), matchedFrom));
            if (matchedTo != null) p = cb.and(p, cb.lessThanOrEqualTo(root.get("matchedAt"), matchedTo));
            return p;
        };

        return hitsRepository.findAll(spec, pageable);
    }

    /**
     * Incremental sync moderation_samples (MySQL) -> ES.
     * Does NOT clear ES index and does NOT cleanup orphans.
     */
    @PostMapping("/samples/sync")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesReindexResponse> syncSamples(
            @RequestParam(value = "onlyEnabled", required = false) Boolean onlyEnabled,
            @RequestParam(value = "batchSize", required = false) Integer batchSize,
            @RequestParam(value = "fromId", required = false) Long fromId
    ) {
        ModerationSamplesReindexResponse res = samplesSyncService.syncIncremental(onlyEnabled, batchSize, fromId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("onlyEnabled", onlyEnabled);
        details.put("batchSize", batchSize);
        details.put("fromId", fromId);
        if (res != null) {
            details.put("total", res.getTotal());
            details.put("success", res.getSuccess());
            details.put("failed", res.getFailed());
            details.put("lastId", res.getLastId());
            details.put("cleared", res.getCleared());
            details.put("clearError", res.getClearError());
        }
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "MODERATION_SAMPLES_SYNC",
                "MODERATION_SAMPLES_INDEX",
                null,
                AuditResult.SUCCESS,
                "增量同步样本到索引",
                null,
                details
        );
        return ResponseEntity.ok(res);
    }

    private static Map<String, Object> summarizeConfig(ModerationSimilarityConfigEntity cfg) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (cfg == null) return m;
        m.put("id", cfg.getId());
        m.put("enabled", cfg.getEnabled());
        m.put("embeddingModel", cfg.getEmbeddingModel());
        m.put("embeddingDims", cfg.getEmbeddingDims());
        m.put("maxInputChars", cfg.getMaxInputChars());
        m.put("defaultTopK", cfg.getDefaultTopK());
        m.put("defaultNumCandidates", cfg.getDefaultNumCandidates());
        return m;
    }

    private static Map<String, Object> summarizeSample(ModerationSamplesEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) return m;
        m.put("id", e.getId());
        m.put("category", e.getCategory());
        m.put("refContentType", e.getRefContentType());
        m.put("refContentId", e.getRefContentId());
        m.put("textHash", e.getTextHash());
        m.put("rawTextLen", e.getRawText() == null ? 0 : e.getRawText().length());
        m.put("riskLevel", e.getRiskLevel());
        m.put("labels", e.getLabels());
        m.put("source", e.getSource());
        m.put("enabled", e.getEnabled());
        return m;
    }

    private static String currentUsernameOrNull() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }
}
