package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class ChatRagAugmentConfigDTO {
    private Boolean enabled;

    private Boolean commentsEnabled;
    private Integer commentTopK;

    private Integer maxPosts;
    private Integer perPostMaxCommentChunks;

    private String includePostContentPolicy;
    private Integer postContentMaxTokens;
    private Integer commentChunkMaxTokens;

    private Boolean debugEnabled;
    private Integer debugMaxChars;
}

