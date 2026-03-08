package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private static final String MASK = "***";

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
        if (message != null) d.putIfAbsent("message", sanitizeMessage(message));
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
        e.setDetails(sanitizeDetails(d));

        auditLogsRepository.save(e);
        AuditLogContextHolder.markWritten();
    }

    private static Map<String, Object> sanitizeDetails(Map<String, Object> in) {
        if (in == null || in.isEmpty()) return in;
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            out.put(k, sanitizeValue(k, v));
        }
        return out;
    }

    private static Object sanitizeValue(String key, Object v) {
        if (key != null && isSensitiveKey(key)) return MASK;
        if (v == null) return null;
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                if (e.getKey() == null) continue;
                String k = String.valueOf(e.getKey());
                out.put(k, sanitizeValue(k, e.getValue()));
            }
            return out;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object x : list) {
                if (x instanceof Map<?, ?> || x instanceof List<?>) out.add(sanitizeValue(null, x));
                else out.add(x);
            }
            return out;
        }
        if (v instanceof String s && key != null && key.toLowerCase(Locale.ROOT).contains("message")) {
            return sanitizeMessage(s);
        }
        return v;
    }

    private static boolean isSensitiveKey(String k) {
        if (k == null) return false;
        String s = k.toLowerCase(Locale.ROOT);
        return s.contains("password")
                || s.contains("secret")
                || s.contains("token")
                || s.contains("apikey")
                || s.contains("api_key")
                || s.contains("accesskey")
                || s.contains("access_key")
                || s.contains("privatekey")
                || s.contains("private_key")
                || s.contains("authorization")
                || s.contains("cookie");
    }

    private static String sanitizeMessage(String raw) {
        if (raw == null) return null;
        String s = raw;
        s = s.replaceAll("(?i)(authorization|token|password|cookie|secret|apiKey|privateKey)\\s*[:=]\\s*([^\\s,;]+)", "$1: " + MASK);
        s = s.replaceAll("(?i)(\"(authorization|token|password|cookie|secret|apiKey|privateKey)\"\\s*:\\s*)\"(.*?)\"", "$1\"" + MASK + "\"");
        return s;
    }
}
