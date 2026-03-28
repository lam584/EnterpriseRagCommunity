package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class IndexSyncStatusDTO {
    private boolean indexed;
    private long docCount;
    private String status;
    private String reason;
    private String detail;
    private String indexName;
}
