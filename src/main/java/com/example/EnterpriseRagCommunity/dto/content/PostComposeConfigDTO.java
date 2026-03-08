package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

@Data
public class PostComposeConfigDTO {
    private Boolean requireTitle;
    private Boolean requireTags;
    private Integer maxAttachments;
    private Integer maxContentChars;
    private Integer chunkThresholdChars;
    private Boolean bypassAttachmentLimitWhenChunked;
}

