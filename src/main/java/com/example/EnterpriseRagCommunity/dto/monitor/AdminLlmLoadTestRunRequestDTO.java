package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmLoadTestRunRequestDTO {
    private Integer concurrency;
    private Integer totalRequests;
    private Integer ratioChatStream;
    private Integer ratioModerationTest;
    private String providerId;
    private String model;
    private Boolean stream;
    private Boolean enableThinking;
    private Integer timeoutMs;
    private Integer retries;
    private Integer retryDelayMs;
    private String chatMessage;
    private String moderationText;
}
