package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostSummaryGenHistoryDTO {
    private Long id;
    private Long postId;
    private String status;
    private String model;
    private LocalDateTime createdAt;
    private Long latencyMs;
    private String errorMessage;
}

