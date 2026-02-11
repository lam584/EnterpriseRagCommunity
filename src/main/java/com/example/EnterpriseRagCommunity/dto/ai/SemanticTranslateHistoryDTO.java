package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SemanticTranslateHistoryDTO {
    private Long id;
    private Long userId;
    private LocalDateTime createdAt;

    private String sourceType;
    private Long sourceId;
    private String targetLang;

    private String sourceTitleExcerpt;
    private String sourceContentExcerpt;

    private String translatedTitle;
    private String translatedMarkdown;

    private String model;
    private Double temperature;
    private Double topP;
    private Long latencyMs;
    private Integer promptVersion;
}
