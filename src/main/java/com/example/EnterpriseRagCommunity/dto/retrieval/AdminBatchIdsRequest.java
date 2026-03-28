package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class AdminBatchIdsRequest {
    private List<Long> ids;
}
