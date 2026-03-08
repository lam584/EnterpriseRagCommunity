package com.example.EnterpriseRagCommunity.dto.moderation;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminModerationReviewTraceChunkSetDTO(
        Long id,
        Long queueId,
        String caseType,
        String contentType,
        Long contentId,
        String status,

        Integer chunkThresholdChars,
        Integer chunkSizeChars,
        Integer overlapChars,

        Integer totalChunks,
        Integer completedChunks,
        Integer failedChunks,

        Map<String, Object> configJson,
        Map<String, Object> memoryJson,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

