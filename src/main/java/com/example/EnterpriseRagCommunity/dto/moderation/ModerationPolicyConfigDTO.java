package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ModerationPolicyConfigDTO {
    private Long id;
    private Integer version;

    private ContentType contentType;
    private String policyVersion;
    private Map<String, Object> config;

    private LocalDateTime updatedAt;
    private String updatedBy;
}

