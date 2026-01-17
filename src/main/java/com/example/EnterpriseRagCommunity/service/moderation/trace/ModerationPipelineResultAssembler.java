package com.example.EnterpriseRagCommunity.service.moderation.trace;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineStepDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;

import java.util.List;

public class ModerationPipelineResultAssembler {
    private ModerationPipelineResultAssembler() {}

    public static AdminModerationPipelineRunDetailDTO toDetail(ModerationPipelineRunEntity run, List<ModerationPipelineStepEntity> steps) {
        return new AdminModerationPipelineRunDetailDTO(
                AdminModerationPipelineRunDTO.fromEntity(run),
                steps == null ? List.of() : steps.stream().map(AdminModerationPipelineStepDTO::fromEntity).toList()
        );
    }
}
