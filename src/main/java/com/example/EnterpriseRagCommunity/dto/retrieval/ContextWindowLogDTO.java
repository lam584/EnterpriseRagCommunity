package com.example.EnterpriseRagCommunity.dto.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContextWindowLogDTO {
    private Long id;
    private Long eventId;
    private ContextWindowPolicy policy;
    private Integer budgetTokens;
    private Integer totalTokens;
    private Integer selectedItems;
    private Integer droppedItems;
    private Integer items;
    private String queryText;
    private LocalDateTime createdAt;
}
