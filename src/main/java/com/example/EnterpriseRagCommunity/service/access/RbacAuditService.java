package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.RbacAuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.RbacAuditLogsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RbacAuditService {
    private final RbacAuditLogsRepository rbacAuditLogsRepository;
    private final UsersRepository usersRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogWriter auditLogWriter;

    @Transactional
    public void record(String action, String targetType, String targetId, Object before, Object after) {
        record(action, targetType, targetId, buildDiff(before, after));
    }

    @Transactional
    public void record(String action, String targetType, String targetId, Object diff) {
        RbacAuditLogsEntity e = new RbacAuditLogsEntity();
        e.setActorUserId(resolveActorUserId().orElse(null));
        e.setAction(action);
        e.setTargetType(targetType);
        e.setTargetId(targetId);

        HttpServletRequest req = currentRequest().orElse(null);
        if (req != null) {
            e.setReason(trimToNull(req.getHeader("X-Admin-Reason")));
            e.setRequestId(trimToNull(req.getHeader("X-Request-Id")));
            e.setUserAgent(trimToNull(req.getHeader("User-Agent")));
            e.setRequestIp(extractIp(req));
        }

        e.setDiffJson(toJsonOrNull(diff));
        e.setCreatedAt(LocalDateTime.now());
        rbacAuditLogsRepository.save(e);

        try {
            String actorName = currentEmailOrNull();
            String mappedAction = action == null ? "RBAC_UNKNOWN" : (action.startsWith("RBAC_") ? action : ("RBAC_" + action));

            Map<String, Object> details = new HashMap<>();
            details.put("targetType", targetType);
            details.put("targetId", targetId);
            details.put("diff", diff);
            if (e.getReason() != null) details.put("reason", e.getReason());
            if (e.getRequestId() != null) details.put("requestId", e.getRequestId());
            if (e.getRequestIp() != null) details.put("ip", e.getRequestIp());
            if (e.getUserAgent() != null) details.put("userAgent", e.getUserAgent());

            auditLogWriter.write(
                    e.getActorUserId(),
                    actorName,
                    mappedAction,
                    "RBAC",
                    null,
                    AuditResult.SUCCESS,
                    "RBAC 变更：" + mappedAction,
                    null,
                    details
            );
        } catch (Exception ignore) {
        }
    }

    private Map<String, Object> buildDiff(Object before, Object after) {
        Map<String, Object> m = new HashMap<>();
        m.put("before", before);
        m.put("after", after);
        return m;
    }

    private Optional<Long> resolveActorUserId() {
        String email = currentEmailOrNull();
        if (email == null) return Optional.empty();
        return usersRepository.findByEmailAndIsDeletedFalse(email).map(UsersEntity::getId);
    }

    private Optional<HttpServletRequest> currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return Optional.of(sra.getRequest());
        }
        return Optional.empty();
    }

    private String toJsonOrNull(Object v) {
        if (v == null) return null;
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String extractIp(HttpServletRequest req) {
        String forwarded = trimToNull(req.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            int idx = forwarded.indexOf(',');
            return idx > 0 ? forwarded.substring(0, idx).trim() : forwarded;
        }
        String realIp = trimToNull(req.getHeader("X-Real-IP"));
        if (realIp != null) return realIp;
        return trimToNull(req.getRemoteAddr());
    }

    private static String currentEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
