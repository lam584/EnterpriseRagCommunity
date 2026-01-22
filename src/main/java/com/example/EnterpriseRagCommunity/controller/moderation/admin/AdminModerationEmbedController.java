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
import com.example.EnterpriseRagCommunity.config.ModerationSimilarityProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleCreateRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSampleUpdateRequest;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSampleTextUtils;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesReindexResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationSamplesSyncResult;
import com.example.EnterpriseRagCommunity.service.moderation.es.ModerationSamplesSyncService;
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
    private final ModerationSimilarityProperties similarityProps;
    private final ModerationSamplesRepository samplesRepository;
    private final ModerationSimilarHitsRepository hitsRepository;
    private final ModerationSamplesSyncService samplesSyncService;

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
            e.setEmbeddingModel(similarityProps.getEs().getEmbeddingModel());
            e.setEmbeddingDims(similarityProps.getEs().getEmbeddingDims());
            e.setMaxInputChars(0);
            e.setDefaultTopK(similarityProps.getEs().getTopK());
            e.setDefaultThreshold(similarityProps.getEs().getThreshold());
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
            e.setEmbeddingModel(similarityProps.getEs().getEmbeddingModel());
            e.setEmbeddingDims(similarityProps.getEs().getEmbeddingDims());
            e.setMaxInputChars(0);
            e.setDefaultTopK(similarityProps.getEs().getTopK());
            e.setDefaultThreshold(similarityProps.getEs().getThreshold());
            e.setDefaultNumCandidates(0);
            e.setUpdatedAt(LocalDateTime.now());
            return e;
        });

        cfg.setEnabled(enabled);
        if (payload != null) {
            if (payload.getEmbeddingModel() != null) {
                String m = payload.getEmbeddingModel().trim();
                cfg.setEmbeddingModel(m.isBlank() ? null : m);
            }
            if (payload.getEmbeddingDims() != null) cfg.setEmbeddingDims(Math.max(0, payload.getEmbeddingDims()));
            if (payload.getMaxInputChars() != null) cfg.setMaxInputChars(Math.max(0, payload.getMaxInputChars()));
            if (payload.getDefaultTopK() != null) cfg.setDefaultTopK(Math.max(1, Math.min(50, payload.getDefaultTopK())));
            if (payload.getDefaultThreshold() != null) cfg.setDefaultThreshold(Math.max(0, Math.min(1, payload.getDefaultThreshold())));
            if (payload.getDefaultNumCandidates() != null) cfg.setDefaultNumCandidates(Math.max(0, payload.getDefaultNumCandidates()));
        }
        cfg.setUpdatedAt(LocalDateTime.now());
        cfg = configRepository.save(cfg);
        return ResponseEntity.ok(cfg);
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

        Specification<ModerationSamplesEntity> spec = (root, _, cb) -> {
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
        if (!samplesRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sample not found");
        }
        samplesRepository.deleteById(id);

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
        return ResponseEntity.ok(samplesSyncService.reindexAll(onlyEnabled, batchSize, fromId));
    }

    /**
     * Optional helper: manually upsert one sample to ES.
     */
    @PostMapping("/samples/{id}/sync")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public ResponseEntity<ModerationSamplesSyncResult> syncOne(@PathVariable("id") Long id) {
        return ResponseEntity.ok(samplesSyncService.upsertById(id));
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

        Specification<ModerationSimilarHitsEntity> spec = (root, _, cb) -> {
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
        return ResponseEntity.ok(samplesSyncService.syncIncremental(onlyEnabled, batchSize, fromId));
    }
}
