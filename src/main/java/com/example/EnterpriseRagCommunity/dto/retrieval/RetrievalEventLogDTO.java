package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RetrievalEventLogDTO {
    private Long id;
    private Long userId;
    private String queryText;
    private Integer bm25K;
    private Integer vecK;
    private Integer hybridK;
    private String rerankModel;
    private Integer rerankK;
    private LocalDateTime createdAt;
}
