package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaSearchHitDTO {
    private String type; // SESSION_TITLE | MESSAGE
    private Long sessionId;
    private Long messageId;
    private String title;
    private String snippet;
    private LocalDateTime createdAt;
}

