package com.example.EnterpriseRagCommunity.dto.access;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RbacAuditQueryDTO {
    private Integer pageNum;
    private Integer pageSize;
    private Long actorUserId;
    private String action;
    private String targetType;
    private String targetId;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
}

