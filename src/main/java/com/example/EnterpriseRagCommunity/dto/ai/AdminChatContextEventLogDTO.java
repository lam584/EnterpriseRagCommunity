package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminChatContextEventLogDTO {
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long questionMessageId;
    private String kind;
    private String reason;
    private Integer beforeTokens;
    private Integer afterTokens;
    private Integer beforeChars;
    private Integer afterChars;
    private Integer latencyMs;
    private LocalDateTime createdAt;
}
