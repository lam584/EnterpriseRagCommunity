package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;

import java.util.Locale;

final class ModerationStageSupport {

    private ModerationStageSupport() {
    }

    static QueueStage mapNextStage(String action) {
        String normalized = normalizeAction(action);
        if (normalized == null) {
            return QueueStage.HUMAN;
        }
        return switch (normalized) {
            case "LLM" -> QueueStage.LLM;
            case "VEC" -> QueueStage.VEC;
            default -> QueueStage.HUMAN;
        };
    }

    static String normalizeAction(String action) {
        String normalized = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return normalized;
    }
}
