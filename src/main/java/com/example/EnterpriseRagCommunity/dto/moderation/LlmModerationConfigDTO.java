package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmModerationConfigDTO {
    private Long id;
    private Integer version;

    private String multimodalPromptCode;
    private String judgePromptCode;

    private Boolean autoRun;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
