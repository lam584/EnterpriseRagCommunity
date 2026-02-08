package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminTokenTimelinePointDTO {
    private LocalDateTime time;
    private Long tokensIn;
    private Long tokensOut;
    private Long totalTokens;
}

