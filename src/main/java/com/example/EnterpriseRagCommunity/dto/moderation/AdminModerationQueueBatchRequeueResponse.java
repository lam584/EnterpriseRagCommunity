package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.util.List;

@Data
public class AdminModerationQueueBatchRequeueResponse {
    private Integer total;
    private Integer success;
    private Integer failed;
    private List<Long> successIds;
    private List<FailedItem> failedItems;

    @Data
    public static class FailedItem {
        private Long id;
        private String error;
    }
}

