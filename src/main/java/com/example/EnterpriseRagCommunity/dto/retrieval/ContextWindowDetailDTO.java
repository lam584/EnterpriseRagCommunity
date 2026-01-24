package com.example.EnterpriseRagCommunity.dto.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ContextWindowDetailDTO {
    private Long id;
    private Long eventId;
    private String queryText;
    private ContextWindowPolicy policy;
    private Integer totalTokens;
    private Map<String, Object> chunkIds;
    private LocalDateTime createdAt;
}

