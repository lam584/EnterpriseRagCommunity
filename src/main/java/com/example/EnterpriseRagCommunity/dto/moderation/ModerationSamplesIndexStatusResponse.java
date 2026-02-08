package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class ModerationSamplesIndexStatusResponse {
    private String indexName;
    private Boolean exists;
    private Boolean available;
    private String availabilityMessage;
    private Long docCount;
    private Integer embeddingDimsConfigured;
    private Integer embeddingDimsInMapping;
    private String lastIncrementalSyncAt;
}
