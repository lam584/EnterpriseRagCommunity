package com.example.EnterpriseRagCommunity.dto.ai;

import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaMessageDTO {
    private Long id;
    private Long sessionId;
    private MessageRole role;
    private String content;
    private String model;
    private Integer tokensIn;
    private Integer tokensOut;
    private LocalDateTime createdAt;
}

