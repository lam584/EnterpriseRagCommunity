package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class CitationConfigDTO {
    private Boolean enabled;

    private String citationMode;
    private String instructionTemplate;

    private String sourcesTitle;
    private Integer maxSources;

    private Boolean includeUrl;
    private Boolean includeScore;
    private Boolean includeTitle;
    private Boolean includePostId;
    private Boolean includeChunkIndex;

    private String postUrlTemplate;
}

