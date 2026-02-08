package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class AdminLlmRoutingScenarioDTO {
    private String taskType;
    private String label;
    private String category;
    private Integer sortIndex;
}
