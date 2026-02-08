package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmRoutingDecisionEventDTO {
    private Long tsMs;
    private String kind;
    private String taskType;
    private Integer attempt;
    private String taskId;
    private String providerId;
    private String modelName;
    private Boolean ok;
    private String errorCode;
    private String errorMessage;
    private Long latencyMs;
    private String apiSource;
}
