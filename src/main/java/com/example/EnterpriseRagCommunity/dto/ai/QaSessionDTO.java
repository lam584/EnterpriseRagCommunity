package com.example.EnterpriseRagCommunity.dto.ai;

import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaSessionDTO {
    private Long id;
    private String title;
    private ContextStrategy contextStrategy;
    private Boolean isActive;
    private LocalDateTime createdAt;

    // optional summary fields for list page
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
}
