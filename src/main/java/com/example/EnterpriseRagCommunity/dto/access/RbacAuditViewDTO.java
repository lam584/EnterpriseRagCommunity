package com.example.EnterpriseRagCommunity.dto.access;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RbacAuditViewDTO {
    private Long id;
    private Long actorUserId;
    private String action;
    private String targetType;
    private String targetId;
    private String reason;
    private String diffJson;
    private String requestIp;
    private String userAgent;
    private String requestId;
    private LocalDateTime createdAt;
}

