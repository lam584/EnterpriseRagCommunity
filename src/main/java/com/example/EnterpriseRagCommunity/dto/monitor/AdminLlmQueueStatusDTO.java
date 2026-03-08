package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmQueueStatusDTO {
    private Long snapshotAtMs;
    private Boolean stale;
    private Boolean truncated;
    private Integer maxConcurrent;
    private Integer runningCount;
    private Integer pendingCount;
    private List<AdminLlmQueueTaskDTO> running;
    private List<AdminLlmQueueTaskDTO> pending;
    private List<AdminLlmQueueTaskDTO> recentCompleted;
    private List<AdminLlmQueueSampleDTO> samples;
}
