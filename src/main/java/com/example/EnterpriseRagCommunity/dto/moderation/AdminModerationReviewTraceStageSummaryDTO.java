package com.example.EnterpriseRagCommunity.dto.moderation;

import java.math.BigDecimal;
import java.util.Map;

public record AdminModerationReviewTraceStageSummaryDTO(
        String stage,
        String decision,
        BigDecimal score,
        BigDecimal threshold,
        Long costMs,
        Map<String, Object> details
) {
}

