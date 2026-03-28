package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class PostIndexSyncStatusDTO {
    private Long postId;
    private IndexSyncStatusDTO postIndex;
    private IndexSyncStatusDTO attachmentIndex;
}
