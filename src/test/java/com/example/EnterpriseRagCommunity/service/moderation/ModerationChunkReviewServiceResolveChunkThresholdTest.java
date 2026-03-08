package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModerationChunkReviewServiceResolveChunkThresholdTest {

    @Test
    void resolvesFromFallbackConfigWhenPresent() {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setChunkThresholdChars(20_000);

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkThresholdChars(25_000);

        assertEquals(25_000, ModerationChunkReviewService.resolveChunkThreshold(cfg, fb));
    }

    @Test
    void fallsBackToChunkReviewConfigWhenFallbackMissing() {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setChunkThresholdChars(18_000);

        assertEquals(18_000, ModerationChunkReviewService.resolveChunkThreshold(cfg, null));
    }

    @Test
    void usesDefaultWhenBothMissing() {
        assertEquals(20_000, ModerationChunkReviewService.resolveChunkThreshold(null, null));
    }

    @Test
    void clampsToSafeRange() {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setChunkThresholdChars(999);
        assertEquals(1000, ModerationChunkReviewService.resolveChunkThreshold(cfg, null));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkThresholdChars(6_000_000);
        assertEquals(5_000_000, ModerationChunkReviewService.resolveChunkThreshold(cfg, fb));
    }
}

