package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class AdminLlmRoutingTargetDTO {
    private String taskType;
    private String providerId;
    private String modelName;
    private Boolean enabled;
    private Integer weight;
    private Integer priority;
    private Integer sortIndex;
    private Double qps;
    private Long priceConfigId;
}
