package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class AdminChatContextEventDetailDTO {
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long questionMessageId;
    private String kind;
    private String reason;
    private Integer targetPromptTokens;
    private Integer reserveAnswerTokens;
    private Integer beforeTokens;
    private Integer afterTokens;
    private Integer beforeChars;
    private Integer afterChars;
    private Integer latencyMs;
    private Map<String, Object> detailJson;
    private LocalDateTime createdAt;
}
