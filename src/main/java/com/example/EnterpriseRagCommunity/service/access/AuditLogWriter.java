package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small helper to write audit logs consistently.
 *
 * Notes:
 * - Project stores actorName/traceId/message/ip in details JSON.
 * - This writer keeps API stable for existing admin audit log UI.
 */
@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogsRepository auditLogsRepository;

    public void writeSystem(
            String action,
            String entityType,
            Long entityId,
            AuditResult result,
            String message,
            String traceId,
            Map<String, Object> details
    ) {
        write(null, null, action, entityType, entityId, result, message, traceId, details);
    }

    public void write(
            Long actorUserId,
            String actorName,
            String action,
            String entityType,
            Long entityId,
            AuditResult result,
            String message,
            String traceId,
            Map<String, Object> details
    ) {
        AuditLogsEntity e = new AuditLogsEntity();
        e.setTenantId(null);
        e.setActorUserId(actorUserId);
        e.setAction(action == null ? "UNKNOWN" : action);
        e.setEntityType(entityType == null ? "UNKNOWN" : entityType);
        e.setEntityId(entityId);
        e.setResult(result == null ? AuditResult.FAIL : result);
        e.setCreatedAt(LocalDateTime.now());

        Map<String, Object> d = new LinkedHashMap<>();
        if (details != null) d.putAll(details);
        if (actorName != null) d.putIfAbsent("actorName", actorName);
        if (traceId != null) d.putIfAbsent("traceId", traceId);
        if (message != null) d.putIfAbsent("message", message);
        RequestAuditContextHolder.RequestAuditContext ctx = RequestAuditContextHolder.get();
        if (ctx != null) {
            if (ctx.clientIp() != null) d.putIfAbsent("ip", ctx.clientIp());
            if (ctx.requestId() != null) d.putIfAbsent("requestId", ctx.requestId());
            if (traceId == null && ctx.traceId() != null) d.putIfAbsent("traceId", ctx.traceId());
            if (ctx.method() != null) d.putIfAbsent("method", ctx.method());
            if (ctx.path() != null) d.putIfAbsent("path", ctx.path());
            if (ctx.scheme() != null) d.putIfAbsent("scheme", ctx.scheme());
            if (ctx.host() != null) d.putIfAbsent("host", ctx.host());
            if (ctx.clientPort() != null) d.putIfAbsent("clientPort", ctx.clientPort());
            if (ctx.serverIp() != null) d.putIfAbsent("serverIp", ctx.serverIp());
            if (ctx.serverPort() != null) d.putIfAbsent("serverPort", ctx.serverPort());
            if (ctx.userAgent() != null) d.putIfAbsent("userAgent", ctx.userAgent());
            if (ctx.referer() != null) d.putIfAbsent("referer", ctx.referer());
            if (ctx.details() != null && !ctx.details().isEmpty()) d.putIfAbsent("req", ctx.details());
        }
        e.setDetails(d);

        auditLogsRepository.save(e);
        AuditLogContextHolder.markWritten();
    }
}
