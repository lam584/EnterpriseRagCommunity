package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmLoadTestResultDTO {
    private Integer index;
    private String kind;
    private Boolean ok;
    private Long latencyMs;
    private Long startedAtMs;
    private Long finishedAtMs;
    private String error;
    private Integer tokens;
    private Integer tokensIn;
    private Integer tokensOut;
    private String model;
}
