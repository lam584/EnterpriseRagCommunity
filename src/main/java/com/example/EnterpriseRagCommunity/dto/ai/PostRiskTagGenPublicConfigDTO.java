package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PostRiskTagGenPublicConfigDTO {
    private Boolean enabled;
    private Integer maxCount;
    private Integer maxContentChars;
}
