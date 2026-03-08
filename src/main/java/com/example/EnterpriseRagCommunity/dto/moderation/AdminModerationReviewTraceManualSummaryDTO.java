package com.example.EnterpriseRagCommunity.dto.moderation;

import java.time.LocalDateTime;

public record AdminModerationReviewTraceManualSummaryDTO(
        Boolean hasManual,
        String lastAction,
        String lastActorName,
        Long lastActorId,
        LocalDateTime lastAt
) {
}

