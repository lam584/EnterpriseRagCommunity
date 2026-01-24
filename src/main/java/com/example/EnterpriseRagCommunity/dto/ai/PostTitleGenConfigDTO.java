package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostTitleGenConfigDTO {
    private Long id;
    private Integer version;

    private Boolean enabled;
    private String systemPrompt;
    private String promptTemplate;

    private String model;
    private Double temperature;

    private Integer defaultCount;
    private Integer maxCount;
    private Integer maxContentChars;

    private Boolean historyEnabled;
    private Integer historyKeepDays;
    private Integer historyKeepRows;

    private LocalDateTime updatedAt;
    private String updatedBy;
}

