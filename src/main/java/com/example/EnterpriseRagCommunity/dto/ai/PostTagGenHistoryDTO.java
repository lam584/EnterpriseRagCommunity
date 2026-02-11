package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostTagGenHistoryDTO {
    private Long id;
    private Long userId;
    private LocalDateTime createdAt;

    private String boardName;
    private String titleExcerpt;

    private Integer requestedCount;
    private Integer appliedMaxContentChars;
    private Integer contentLen;
    private String contentExcerpt;

    private List<String> tags;
    private String model;
    private Double temperature;
    private Double topP;
    private Long latencyMs;
    private Integer promptVersion;
}
