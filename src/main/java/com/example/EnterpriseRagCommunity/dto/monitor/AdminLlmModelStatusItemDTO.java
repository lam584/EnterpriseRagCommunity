package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.util.List;

@Data
public class AdminLlmModelStatusItemDTO {
    private String providerId;
    private String modelName;
    private Integer runningCount;
    private List<AdminLlmModelCallRecordDTO> records;
}

