package com.example.EnterpriseRagCommunity.dto.monitor;

import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskStatus;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLlmQueueTaskDetailDTO {
    private String id;
    private Long seq;
    private Integer priority;
    private LlmQueueTaskType type;
    private String label;
    private LlmQueueTaskStatus status;
    private String providerId;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long waitMs;
    private Long durationMs;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer totalTokens;
    private Double tokensPerSec;
    private String error;
    private String input;
    private String output;
}
