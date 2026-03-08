package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.List;

@Data
public class AdminModerationQueueBatchRequeueRequest {
    private List<Long> ids;
    private String reason;
    private String reviewStage;
}

