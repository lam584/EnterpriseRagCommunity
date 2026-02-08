package com.example.EnterpriseRagCommunity.service.moderation.es;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesIndexConfigEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationSamplesIndexConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ModerationSamplesIndexConfigService {

    public static final String DEFAULT_INDEX_NAME = "ad_violation_samples_v1";
    public static final boolean DEFAULT_IK_ENABLED = true;
    public static final String DEFAULT_EMBEDDING_MODEL = "";
    public static final int DEFAULT_EMBEDDING_DIMS = 0;
    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_THRESHOLD = 0.15;

    private final ModerationSamplesIndexConfigRepository repository;

    public ModerationSamplesIndexConfigEntity getOrSeedDefault(Long updatedBy) {
        ModerationSamplesIndexConfigEntity existing = getConfigOrNull();
        if (existing != null) return existing;

        ModerationSamplesIndexConfigEntity e = new ModerationSamplesIndexConfigEntity();
        e.setIndexName(DEFAULT_INDEX_NAME);
        e.setIkEnabled(DEFAULT_IK_ENABLED);
        e.setEmbeddingModel(null);
        e.setEmbeddingDims(DEFAULT_EMBEDDING_DIMS);
        e.setDefaultTopK(DEFAULT_TOP_K);
        e.setDefaultThreshold(DEFAULT_THRESHOLD);
        e.setUpdatedAt(LocalDateTime.now());
        e.setUpdatedBy(updatedBy);
        return repository.save(e);
    }

    public ModerationSamplesIndexConfigEntity getConfigOrNull() {
        try {
            return repository.findAll().stream().findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    public String getIndexNameOrDefault() {
        ModerationSamplesIndexConfigEntity cfg = getConfigOrNull();
        String v = cfg == null ? null : toNonBlank(cfg.getIndexName());
        return v == null ? DEFAULT_INDEX_NAME : v;
    }

    public boolean isIkEnabledOrDefault() {
        ModerationSamplesIndexConfigEntity cfg = getConfigOrNull();
        Boolean v = cfg == null ? null : cfg.getIkEnabled();
        return v == null ? DEFAULT_IK_ENABLED : v;
    }

    public String getEmbeddingModelOrDefault() {
        ModerationSamplesIndexConfigEntity cfg = getConfigOrNull();
        String v = cfg == null ? null : toNonBlank(cfg.getEmbeddingModel());
        return v == null ? DEFAULT_EMBEDDING_MODEL : v;
    }

    public int getEmbeddingDimsOrDefault() {
        ModerationSamplesIndexConfigEntity cfg = getConfigOrNull();
        Integer v = cfg == null ? null : cfg.getEmbeddingDims();
        int d = v == null ? DEFAULT_EMBEDDING_DIMS : v;
        return Math.max(0, d);
    }

    public int getDefaultTopKOrDefault() {
        ModerationSamplesIndexConfigEntity cfg = getConfigOrNull();
        Integer v = cfg == null ? null : cfg.getDefaultTopK();
        int k = v == null ? DEFAULT_TOP_K : v;
        return Math.max(1, Math.min(50, k));
    }

    public double getDefaultThresholdOrDefault() {
        ModerationSamplesIndexConfigEntity cfg = getConfigOrNull();
        Double v = cfg == null ? null : cfg.getDefaultThreshold();
        double t = v == null ? DEFAULT_THRESHOLD : v;
        if (t < 0) return 0;
        if (t > 1) return 1;
        return t;
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
