package com.example.EnterpriseRagCommunity.dto.moderation;

import java.util.List;

public record AdminModerationPipelineRunHistoryPageDTO(
        List<AdminModerationPipelineRunHistoryItemDTO> content,
        int totalPages,
        long totalElements,
        int page,
        int pageSize
) {
}
