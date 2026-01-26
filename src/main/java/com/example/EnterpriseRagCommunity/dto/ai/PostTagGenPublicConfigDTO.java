package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PostTagGenPublicConfigDTO {
    private Boolean enabled;
    private Integer defaultCount;
    private Integer maxCount;
    private Integer maxContentChars;
}

