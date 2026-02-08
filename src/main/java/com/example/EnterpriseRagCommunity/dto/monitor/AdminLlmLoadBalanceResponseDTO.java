package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmLoadBalanceResponseDTO {
    private String range;
    private Long startMs;
    private Long endMs;
    private Integer bucketSec;
    private List<AdminLlmLoadBalanceModelDTO> models;
}

