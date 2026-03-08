package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

@Data
public class ModerationChunkReviewConfigDTO {
    private Boolean enabled;
    private Integer chunkThresholdChars;
    private Integer chunkSizeChars;
    private Integer overlapChars;
    private Integer maxChunksTotal;
    private Integer chunksPerRun;
    private Integer maxConcurrentWorkers;
    private Integer maxAttempts;

    private Boolean enableTempIndexHints;
    private Boolean enableContextCompress;
    private Boolean enableGlobalMemory;
    private Boolean sendImagesOnlyWhenInEvidence;
    private Boolean includeImagesBlockOnlyForEvidenceMatches;

    private Boolean queueAutoRefreshEnabled;
    private Integer queuePollIntervalMs;
    
    private String chunkMode;

    public static ModerationChunkReviewConfigDTO empty() {
        return new ModerationChunkReviewConfigDTO();
    }
}
