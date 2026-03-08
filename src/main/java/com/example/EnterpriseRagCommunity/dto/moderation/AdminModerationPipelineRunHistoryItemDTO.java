package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;

import java.time.LocalDateTime;

/**
 * 列表用 DTO（轻量）：不含 steps，点开后再调用 /{runId} 拉取详情。
 */
public record AdminModerationPipelineRunHistoryItemDTO(
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
        LocalDateTime createdAt
) {
    public static AdminModerationPipelineRunHistoryItemDTO fromEntity(ModerationPipelineRunEntity e) {
        if (e == null) return null;
        return new AdminModerationPipelineRunHistoryItemDTO(
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
                e.getCreatedAt()
        );
    }
}
