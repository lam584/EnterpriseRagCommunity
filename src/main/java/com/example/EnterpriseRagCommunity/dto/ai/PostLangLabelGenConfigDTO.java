package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostLangLabelGenConfigDTO {
    private Long id;
    private Integer version;

    private Boolean enabled;
    private String systemPrompt;
    private String promptTemplate;

    private String model;
    private String providerId;
    private Double temperature;
    private Integer maxContentChars;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
