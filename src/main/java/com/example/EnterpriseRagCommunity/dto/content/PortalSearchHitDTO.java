package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PortalSearchHitDTO {
    private String type;

    private Long postId;
    private Long commentId;
    private Long fileAssetId;

    private String title;
    private String snippet;
    private String highlightedTitle;
    private String highlightedSnippet;

    private Double score;
    private LocalDateTime createdAt;
    private String url;
}
