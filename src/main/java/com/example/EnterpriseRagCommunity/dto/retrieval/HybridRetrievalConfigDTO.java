package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class HybridRetrievalConfigDTO {
    private Boolean enabled;

    private Integer bm25K;
    private Double bm25TitleBoost;
    private Double bm25ContentBoost;

    private Integer vecK;
    private Boolean fileVecEnabled;
    private Integer fileVecK;

    private Integer hybridK;
    private String fusionMode;
    private Double bm25Weight;
    private Double vecWeight;
    private Double fileVecWeight;
    private Integer rrfK;

    private Boolean rerankEnabled;
    private String rerankModel;
    private Double rerankTemperature;
    private Double rerankTopP;
    private Integer rerankK;
    private Integer rerankTimeoutMs;
    private Integer rerankSlowThresholdMs;

    private Integer maxDocs;
    private Integer perDocMaxTokens;
    private Integer maxInputTokens;
}
