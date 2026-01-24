package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class PostTitleGenPublicConfigDTO {
    private Boolean enabled;
    private Integer defaultCount;
    private Integer maxCount;
}

