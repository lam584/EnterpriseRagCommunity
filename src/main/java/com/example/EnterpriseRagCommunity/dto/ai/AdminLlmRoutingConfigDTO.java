package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmRoutingConfigDTO {
    private List<AdminLlmRoutingScenarioDTO> scenarios;
    private List<AdminLlmRoutingPolicyDTO> policies;
    private List<AdminLlmRoutingTargetDTO> targets;
}
