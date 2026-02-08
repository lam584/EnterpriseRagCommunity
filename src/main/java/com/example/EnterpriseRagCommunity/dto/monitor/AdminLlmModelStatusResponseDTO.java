package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmModelStatusResponseDTO {
    private Long checkedAtMs;
    private Integer windowSec;
    private Integer perModel;
    private List<AdminLlmModelStatusItemDTO> models;
}

