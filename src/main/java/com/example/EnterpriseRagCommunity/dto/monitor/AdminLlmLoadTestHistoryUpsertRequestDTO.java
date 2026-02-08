package com.example.EnterpriseRagCommunity.dto.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class AdminLlmLoadTestHistoryUpsertRequestDTO {
    private String runId;
    private JsonNode summary;
}
