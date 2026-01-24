package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class RagAutoSyncConfigDTO {
    private Boolean enabled;
    private Long intervalSeconds;
}

