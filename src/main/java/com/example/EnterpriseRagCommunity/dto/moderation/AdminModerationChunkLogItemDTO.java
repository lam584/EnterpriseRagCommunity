package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AdminModerationChunkLogItemDTO {
    private Long id;
    private Long chunkSetId;
    private Long queueId;
    private String caseType;
    private String contentType;
    private Long contentId;

    private String sourceType;
    private String sourceKey;
    private Long fileAssetId;
    private String fileName;
    private Integer chunkIndex;
    private Integer startOffset;
    private Integer endOffset;

    private String status;
    private String verdict;
    private Double confidence;
    private Integer attempts;
    private String lastError;
    private String model;
    private Integer tokensIn;
    private Integer tokensOut;
    private Map<String, Object> budgetConvergenceLog;

    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

