package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationLlmAutoRunnerChunkedThresholdAggregationTest {

    private static Verdict aggregate(AdminModerationChunkProgressDTO progress, ModerationConfidenceFallbackConfigEntity fb) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "aggregateChunkVerdict",
                AdminModerationChunkProgressDTO.class,
                ModerationConfidenceFallbackConfigEntity.class,
                Map.class,
                String.class,
                List.class
        );
        m.setAccessible(true);
        return (Verdict) m.invoke(null, progress, fb, null, null, null);
    }

    @Test
    void aggregateTreatsLowScoreReviewAsApprove_whenBelowChunkHumanThreshold() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmRejectThreshold(0.75);
        fb.setChunkLlmHumanThreshold(0.5);

        AdminModerationChunkProgressDTO.ChunkItem c = new AdminModerationChunkProgressDTO.ChunkItem();
        c.setVerdict("REVIEW");
        c.setScore(0.1);

        AdminModerationChunkProgressDTO p = new AdminModerationChunkProgressDTO();
        p.setChunks(List.of(c));

        assertEquals(Verdict.APPROVE, aggregate(p, fb));
    }

    @Test
    void aggregateReturnsReview_whenAnyChunkScoreBetweenHumanAndReject() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmRejectThreshold(0.75);
        fb.setChunkLlmHumanThreshold(0.5);

        AdminModerationChunkProgressDTO.ChunkItem c = new AdminModerationChunkProgressDTO.ChunkItem();
        c.setVerdict("APPROVE");
        c.setScore(0.6);

        AdminModerationChunkProgressDTO p = new AdminModerationChunkProgressDTO();
        p.setChunks(List.of(c));

        assertEquals(Verdict.REVIEW, aggregate(p, fb));
    }

    @Test
    void aggregateReturnsReject_whenAnyChunkScoreAtLeastRejectThreshold() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmRejectThreshold(0.75);
        fb.setChunkLlmHumanThreshold(0.5);

        AdminModerationChunkProgressDTO.ChunkItem c = new AdminModerationChunkProgressDTO.ChunkItem();
        c.setVerdict("APPROVE");
        c.setScore(0.8);

        AdminModerationChunkProgressDTO p = new AdminModerationChunkProgressDTO();
        p.setChunks(List.of(c));

        assertEquals(Verdict.REJECT, aggregate(p, fb));
    }

    @Test
    void aggregateKeepsReview_whenReviewChunkHasNullScore() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmRejectThreshold(0.75);
        fb.setChunkLlmHumanThreshold(0.5);

        AdminModerationChunkProgressDTO.ChunkItem c = new AdminModerationChunkProgressDTO.ChunkItem();
        c.setVerdict("REVIEW");
        c.setScore(null);

        AdminModerationChunkProgressDTO p = new AdminModerationChunkProgressDTO();
        p.setChunks(List.of(c));

        assertEquals(Verdict.REVIEW, aggregate(p, fb));
    }
}
