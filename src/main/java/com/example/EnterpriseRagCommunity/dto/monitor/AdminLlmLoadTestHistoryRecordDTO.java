package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLlmLoadTestHistoryRecordDTO {
    private String runId;
    private LocalDateTime createdAt;
    private String providerId;
    private String model;
    private Boolean stream;
    private Boolean enableThinking;
    private Integer retries;
    private Integer retryDelayMs;
    private Integer timeoutMs;
    private JsonNode summary;
}
