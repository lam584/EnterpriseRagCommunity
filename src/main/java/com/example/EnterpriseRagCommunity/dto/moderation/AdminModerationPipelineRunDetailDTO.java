package com.example.EnterpriseRagCommunity.dto.moderation;

import java.util.List;

public record AdminModerationPipelineRunDetailDTO(
        AdminModerationPipelineRunDTO run,
        List<AdminModerationPipelineStepDTO> steps
) {
}
