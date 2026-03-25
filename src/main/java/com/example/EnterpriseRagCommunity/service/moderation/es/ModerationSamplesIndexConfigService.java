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

    private final ModerationSamplesIndexConfigRepository repository;

    public ModerationSamplesIndexConfigEntity getOrSeedDefault(Long updatedBy) {
        ModerationSamplesIndexConfigEntity existing = getConfigOrNull();
        if (existing != null) return existing;

        ModerationSamplesIndexConfigEntity e = new ModerationSamplesIndexConfigEntity();
        e.setIndexName(DEFAULT_INDEX_NAME);
        e.setIkEnabled(DEFAULT_IK_ENABLED);
        e.setEmbeddingModel(null);
        e.setEmbeddingDims(DEFAULT_EMBEDDING_DIMS);
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

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
