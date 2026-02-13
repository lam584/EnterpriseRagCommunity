package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Admin view DTO for audit logs.
 *
 * Notes:
 * - Keeps fields aligned with frontend auditLogService.ts contract.
 * - actorName/traceId/message/ip are stored in details for now (nullable).
 */
public record AuditLogsViewDTO(
        Long id,
        LocalDateTime createdAt,

        Long tenantId,
        Long actorId,
        String actorName,

        String action,
        String entityType,
        Long entityId,

        AuditResult result,
        String message,
        String ip,
        String traceId,

        String method,
        String path,
        Boolean autoCrud,

        Map<String, Object> details
) {
}

