package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class HybridRerankTestHitDTO {
    private Integer index;
    private Double relevanceScore;
    private String docId;
    private String title;
    private String text;
}

