package com.example.EnterpriseRagCommunity.dto.content.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostFileExtractionAdminListItemDTO {
    private Long attachmentId;
    private Long postId;
    private Long fileAssetId;

    private String url;
    private String fileName;
    private String originalName;

    private String mimeType;
    private Long sizeBytes;
    private String ext;

    private String extractStatus;
    private LocalDateTime extractionUpdatedAt;
    private String extractionErrorMessage;

    private Long parseDurationMs;
    private Integer pages;
    private Long textCharCount;
    private Long textTokenCount;
    private String tokenCountMode;
    private Integer imageCount;
}

