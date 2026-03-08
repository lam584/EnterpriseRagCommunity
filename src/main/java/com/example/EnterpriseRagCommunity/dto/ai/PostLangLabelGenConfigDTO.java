package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostLangLabelGenConfigDTO {
    private Long id;
    private Integer version;

    private Boolean enabled;
    private String promptCode;

    private String model;
    private String providerId;
    private Double temperature;
    private Double topP;
    private Boolean enableThinking;
    private Integer maxContentChars;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
