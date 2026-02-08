package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

@Data
public class AdminLlmModelCallRecordDTO {
    private String taskId;
    private String taskType;
    private String status;
    private Boolean ok;
    private Long tsMs;
    private Long durationMs;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer totalTokens;
    private String errorCode;
    private String errorMessage;
}

