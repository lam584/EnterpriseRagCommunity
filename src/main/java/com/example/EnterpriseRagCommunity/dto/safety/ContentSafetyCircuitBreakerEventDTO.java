package com.example.EnterpriseRagCommunity.dto.safety;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class ContentSafetyCircuitBreakerEventDTO {
    private Instant at;
    private String type;
    private String message;
    private Map<String, Object> details;
}

