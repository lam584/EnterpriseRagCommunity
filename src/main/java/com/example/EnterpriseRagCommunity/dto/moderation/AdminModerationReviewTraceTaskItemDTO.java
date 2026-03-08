package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;

import java.time.LocalDateTime;

public record AdminModerationReviewTraceTaskItemDTO(
        Long queueId,
        ContentType contentType,
        Long contentId,
        String queueStatus,
        String queueStage,
        LocalDateTime queueUpdatedAt,

        Long latestRunId,
        String latestRunStatus,
        String latestFinalDecision,
        String latestTraceId,
        LocalDateTime latestStartedAt,
        LocalDateTime latestEndedAt,
        Long latestTotalMs,

        AdminModerationReviewTraceStageSummaryDTO rule,
        AdminModerationReviewTraceStageSummaryDTO vec,
        AdminModerationReviewTraceStageSummaryDTO llm,
        AdminModerationReviewTraceChunkSummaryDTO chunk,

        AdminModerationReviewTraceManualSummaryDTO manual
) {
}

