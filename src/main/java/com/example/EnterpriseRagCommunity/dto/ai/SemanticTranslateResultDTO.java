package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class SemanticTranslateResultDTO {
    private String targetLang;
    private String translatedTitle;
    private String translatedMarkdown;
    private String model;
    private Long latencyMs;
    private Boolean cached;
}

