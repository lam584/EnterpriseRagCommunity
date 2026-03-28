package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class CommentIndexSyncStatusDTO {
    private Long commentId;
    private IndexSyncStatusDTO commentIndex;
}
