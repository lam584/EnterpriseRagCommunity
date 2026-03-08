package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostTagGenConfigDTO {
    private Long id;
    private Integer version;

    private Boolean enabled;
    private String promptCode;

    private String model;
    private String providerId;
    private Double temperature;
    private Double topP;
    private Boolean enableThinking;

    private Integer defaultCount;
    private Integer maxCount;
    private Integer maxContentChars;

    private Boolean historyEnabled;
    private Integer historyKeepDays;
    private Integer historyKeepRows;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
