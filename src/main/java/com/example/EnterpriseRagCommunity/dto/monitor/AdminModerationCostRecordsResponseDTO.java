package com.example.EnterpriseRagCommunity.dto.monitor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminModerationCostRecordsResponseDTO(
        LocalDateTime start,
        LocalDateTime end,
        String currency,
        Long totalTokens,
        BigDecimal totalCost,
        List<AdminModerationCostRecordDTO> content,
        int totalPages,
        long totalElements,
        int page,
        int pageSize
) {
}

