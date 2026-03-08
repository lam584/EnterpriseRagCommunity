package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class QaCompressContextResultDTO {
    private Long sessionId;
    private Long summaryMessageId;
    private Integer compressedDeletedCount;
    private Integer keptLast;
    private String summary;
}

