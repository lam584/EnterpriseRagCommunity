package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class AdminModerationQueueActionRequest {
    /** 可选：驳回原因/备注 */
    private String reason;
}

