package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class ModerationSamplesAutoSyncConfigDTO {
    private Boolean enabled;
    private Long intervalSeconds;
}
