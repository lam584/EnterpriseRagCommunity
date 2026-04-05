package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueRiskTagItemDTO;

import java.util.ArrayList;
import java.util.List;

public final class ModerationRiskTagSupport {

    private ModerationRiskTagSupport() {
    }

    public static List<String> collectRiskTags(List<RiskLabelingService.RiskTagItem> riskTagItems) {
        List<String> riskTags = new ArrayList<>();
        if (riskTagItems == null) {
            return riskTags;
        }
        for (RiskLabelingService.RiskTagItem item : riskTagItems) {
            if (item == null || item.slug() == null || item.slug().isBlank()) {
                continue;
            }
            riskTags.add(item.slug());
        }
        return riskTags;
    }

    public static List<AdminModerationQueueRiskTagItemDTO> toRiskTagItemDtos(List<RiskLabelingService.RiskTagItem> riskTagItems) {
        List<AdminModerationQueueRiskTagItemDTO> dtos = new ArrayList<>();
        if (riskTagItems == null) {
            return dtos;
        }
        for (RiskLabelingService.RiskTagItem item : riskTagItems) {
            if (item == null || item.slug() == null || item.slug().isBlank()) {
                continue;
            }
            AdminModerationQueueRiskTagItemDTO dto = new AdminModerationQueueRiskTagItemDTO();
            dto.setSlug(item.slug());
            dto.setName(item.name());
            dtos.add(dto);
        }
        return dtos;
    }
}
