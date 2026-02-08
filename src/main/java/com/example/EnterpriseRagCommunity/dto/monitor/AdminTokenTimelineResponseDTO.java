package com.example.EnterpriseRagCommunity.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminTokenTimelineResponseDTO {
    private LocalDateTime start;
    private LocalDateTime end;
    private String source;
    private String bucket;
    private Long totalTokens;
    private List<AdminTokenTimelinePointDTO> points;
}

