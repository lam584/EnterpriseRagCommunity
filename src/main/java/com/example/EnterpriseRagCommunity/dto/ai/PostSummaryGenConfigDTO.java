package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostSummaryGenConfigDTO {
    private Long id;
    private Integer version;

    private Boolean enabled;
    private String model;
    private Double temperature;
    private Integer maxContentChars;
    private String promptTemplate;

    private LocalDateTime updatedAt;
    private String updatedBy;
}

