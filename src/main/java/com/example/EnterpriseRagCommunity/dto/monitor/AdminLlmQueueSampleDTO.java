package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminLlmQueueSampleDTO {
    private LocalDateTime ts;
    private Integer queueLen;
    private Integer running;
    private Double tokensPerSec;
}

