package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmQueueConfigDTO {
    private Integer maxConcurrent;
    private Integer maxQueueSize;
    private Integer keepCompleted;
}
