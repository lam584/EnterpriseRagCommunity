package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class HybridRetrievalConfigDTO {
    private Boolean enabled;

    private Integer bm25K;
    private Double bm25TitleBoost;
    private Double bm25ContentBoost;

    private Integer vecK;

    private Integer hybridK;
    private String fusionMode;
    private Double bm25Weight;
    private Double vecWeight;
    private Integer rrfK;

    private Boolean rerankEnabled;
    private String rerankModel;
    private Double rerankTemperature;
    private Integer rerankK;

    private Integer maxDocs;
    private Integer perDocMaxTokens;
    private Integer maxInputTokens;
}
