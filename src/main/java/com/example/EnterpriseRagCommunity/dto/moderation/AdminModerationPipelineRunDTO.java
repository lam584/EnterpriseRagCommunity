package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminModerationPipelineRunDTO(
        Long id,
        Long queueId,
        ContentType contentType,
        Long contentId,
        String status,
        String finalDecision,
        String traceId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long totalMs,
        String errorCode,
        String errorMessage,
        String llmModel,
        BigDecimal llmThreshold,
        LocalDateTime createdAt
) {
    public static AdminModerationPipelineRunDTO fromEntity(ModerationPipelineRunEntity e) {
        if (e == null) return null;
        return new AdminModerationPipelineRunDTO(
                e.getId(),
                e.getQueueId(),
                e.getContentType(),
                e.getContentId(),
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getFinalDecision() == null ? null : e.getFinalDecision().name(),
                e.getTraceId(),
                e.getStartedAt(),
                e.getEndedAt(),
                e.getTotalMs(),
                e.getErrorCode(),
                e.getErrorMessage(),
                e.getLlmModel(),
                e.getLlmThreshold(),
                e.getCreatedAt()
        );
    }
}
