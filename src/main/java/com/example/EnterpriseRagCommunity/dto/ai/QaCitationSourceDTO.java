package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class QaCitationSourceDTO {
    private Integer index;
    private Long postId;
    private Integer chunkIndex;
    private Double score;
    private String title;
    private String url;
}
