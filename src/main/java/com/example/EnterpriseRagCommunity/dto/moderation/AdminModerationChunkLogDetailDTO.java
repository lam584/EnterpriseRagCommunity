package com.example.EnterpriseRagCommunity.dto.moderation;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AdminModerationChunkLogDetailDTO {
    private Chunk chunk;
    private ChunkSet chunkSet;

    @Data
    public static class Chunk {
        private Long id;
        private Long chunkSetId;
        private Long queueId;
        private String caseType;
        private String contentType;
        private Long contentId;

        private String sourceType;
        private String sourceKey;
        private Long fileAssetId;
        private String fileName;
        private Integer chunkIndex;
        private Integer startOffset;
        private Integer endOffset;

        private String status;
        private Integer attempts;
        private String lastError;
        private String model;
        private String verdict;
        private Double confidence;
        private Map<String, Object> labels;
        private Integer tokensIn;
        private Integer tokensOut;

        private LocalDateTime decidedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class ChunkSet {
        private Long id;
        private Long queueId;
        private String caseType;
        private String contentType;
        private Long contentId;
        private String status;

        private Integer chunkThresholdChars;
        private Integer chunkSizeChars;
        private Integer overlapChars;

        private Integer totalChunks;
        private Integer completedChunks;
        private Integer failedChunks;

        private Map<String, Object> configJson;
        private Map<String, Object> memoryJson;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

