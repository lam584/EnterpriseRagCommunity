package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SemanticTranslateConfigDTO {
    private Long id;
    private Integer version;

    private Boolean enabled;
    private String systemPrompt;
    private String promptTemplate;

    private String model;
    private String providerId;
    private Double temperature;
    private Double topP;
    private Boolean enableThinking;
    private Integer maxContentChars;

    private Boolean historyEnabled;
    private Integer historyKeepDays;
    private Integer historyKeepRows;

    private List<String> allowedTargetLanguages;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
