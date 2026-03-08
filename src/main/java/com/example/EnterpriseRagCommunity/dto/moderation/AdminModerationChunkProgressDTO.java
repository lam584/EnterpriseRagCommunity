package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminModerationChunkProgressDTO {
    private Long queueId;
    private String status;
    private Integer totalChunks;
    private Integer completedChunks;
    private Integer failedChunks;
    private Integer runningChunks;
    private LocalDateTime updatedAt;
    private List<ChunkItem> chunks;

    @Data
    public static class ChunkItem {
        private Long id;
        private String sourceType;
        private Long fileAssetId;
        private String fileName;
        private Integer chunkIndex;
        private Integer startOffset;
        private Integer endOffset;
        private String status;
        private String verdict;
        private Double confidence;
        private Double score;
        private Double riskScore;
        private Integer attempts;
        private String lastError;
        private LocalDateTime decidedAt;
        private Long elapsedMs;
    }
}
