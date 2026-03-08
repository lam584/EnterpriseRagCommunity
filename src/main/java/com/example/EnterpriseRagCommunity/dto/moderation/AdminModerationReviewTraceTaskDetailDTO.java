package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;

import java.util.List;

public record AdminModerationReviewTraceTaskDetailDTO(
        AdminModerationQueueDetailDTO queue,
        AdminModerationPipelineRunDetailDTO latestRun,
        AdminModerationPipelineRunHistoryPageDTO runHistory,
        AdminModerationReviewTraceChunkSetDTO chunkSet,
        AdminModerationChunkProgressDTO chunkProgress,
        List<AuditLogsViewDTO> auditLogs
) {
}

