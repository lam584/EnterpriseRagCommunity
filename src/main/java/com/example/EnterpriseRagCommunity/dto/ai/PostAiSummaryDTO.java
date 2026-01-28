package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostAiSummaryDTO {
    private Long postId;
    private Boolean enabled;
    private String status;
    private String summaryTitle;
    private String summaryText;
    private String model;
    private LocalDateTime generatedAt;
    private Long latencyMs;
    private String errorMessage;
}

