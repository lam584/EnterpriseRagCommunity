package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;

import java.util.Locale;

/**
 * Centralized decision helpers driven by ModerationConfidenceFallbackConfig.
 *
 * Contract:
 * - RULE/VEC return a next-stage or final action decision.
 * - LLM returns final verdict (APPROVE/REJECT/REVIEW).
 */
public final class ModerationFallbackDecisionService {

    private ModerationFallbackDecisionService() {
    }

    public enum Next {
        REJECT,
        HUMAN,
        LLM,
        VEC
    }

    public static Next mapActionToNext(ModerationConfidenceFallbackConfigEntity.Action a) {
        if (a == null) return Next.HUMAN;
        return switch (a) {
            case REJECT -> Next.REJECT;
            case HUMAN -> Next.HUMAN;
            case LLM -> Next.LLM;
        };
    }

    public static QueueStage mapNextToStage(Next n) {
        if (n == null) return QueueStage.HUMAN;
        return switch (n) {
            case HUMAN -> QueueStage.HUMAN;
            case LLM -> QueueStage.LLM;
            case VEC -> QueueStage.VEC;
            case REJECT -> QueueStage.HUMAN; // stage doesn't matter if we reject immediately
        };
    }

    /**
     * LLM score threshold mapping.
     */
    public static Verdict verdictFromLlmScore(Double score, Double rejectThreshold, Double humanThreshold) {
        double s = score == null ? 0.0 : score;
        if (s < 0) s = 0;
        if (s > 1) s = 1;

        double r = rejectThreshold == null ? 0.75 : rejectThreshold;
        double h = humanThreshold == null ? 0.5 : humanThreshold;
        if (h > r) h = r;

        if (s >= r) return Verdict.REJECT;
        if (s >= h) return Verdict.REVIEW;
        return Verdict.APPROVE;
    }

    public static String normalizeDecision(String decision) {
        if (decision == null) return null;
        String d = decision.trim().toUpperCase(Locale.ROOT);
        if (d.isBlank()) return null;
        return d;
    }
}
