package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.List;

@Data
public class AdminModerationQueueRiskTagsRequest {
    private List<String> riskTags;
}
