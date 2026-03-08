package com.example.EnterpriseRagCommunity.dto.moderation;

import java.math.BigDecimal;

public record AdminModerationReviewTraceChunkSummaryDTO(
        Boolean chunked,
        Long chunkSetId,
        Integer totalChunks,
        Integer completedChunks,
        Integer failedChunks,
        BigDecimal maxScore,
        Long avgMs
) {
}

