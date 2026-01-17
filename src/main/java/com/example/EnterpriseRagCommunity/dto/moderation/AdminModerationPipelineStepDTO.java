package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record AdminModerationPipelineStepDTO(
        Long id,
        Long runId,
        String stage,
        Integer stepOrder,
        String decision,
        BigDecimal score,
        BigDecimal threshold,
        Map<String, Object> details,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long costMs,
        String errorCode,
        String errorMessage
) {
    public static AdminModerationPipelineStepDTO fromEntity(ModerationPipelineStepEntity e) {
        if (e == null) return null;
        return new AdminModerationPipelineStepDTO(
                e.getId(),
                e.getRunId(),
                e.getStage() == null ? null : e.getStage().name(),
                e.getStepOrder(),
                e.getDecision(),
                e.getScore(),
                e.getThreshold(),
                e.getDetailsJson(),
                e.getStartedAt(),
                e.getEndedAt(),
                e.getCostMs(),
                e.getErrorCode(),
                e.getErrorMessage()
        );
    }
}
