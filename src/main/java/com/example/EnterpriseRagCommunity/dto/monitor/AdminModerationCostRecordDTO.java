package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminModerationCostRecordDTO(
        Long id,
        ContentType contentType,
        Long contentId,
        Verdict verdict,
        String model,
        Integer tokensIn,
        Integer tokensOut,
        Long totalTokens,
        BigDecimal cost,
        Boolean priceMissing,
        LocalDateTime decidedAt
) {
}

