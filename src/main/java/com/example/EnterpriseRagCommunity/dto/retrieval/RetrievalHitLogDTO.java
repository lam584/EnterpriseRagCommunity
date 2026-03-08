package com.example.EnterpriseRagCommunity.dto.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import lombok.Data;

@Data
public class RetrievalHitLogDTO {
    private Long id;
    private Long eventId;
    private Integer rank;
    private RetrievalHitType hitType;
    private Long postId;
    private Long chunkId;
    private Double score;
}
