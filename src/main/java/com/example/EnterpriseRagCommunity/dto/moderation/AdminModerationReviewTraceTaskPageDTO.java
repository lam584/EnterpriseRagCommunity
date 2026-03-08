package com.example.EnterpriseRagCommunity.dto.moderation;

import java.util.List;

public record AdminModerationReviewTraceTaskPageDTO(
        List<AdminModerationReviewTraceTaskItemDTO> content,
        int totalPages,
        long totalElements,
        int page,
        int pageSize
) {
}

