package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogContextHolder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnBean({AuditLogWriter.class, AdministratorService.class})
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 110)
public class CrudAuditFilter extends OncePerRequestFilter {

    private final AuditLogWriter auditLogWriter;
    private final AdministratorService administratorService;

    @Value("${app.logging.audit.auto-crud.enabled:true}")
    private boolean autoCrudEnabled;

    @Value("${app.logging.audit.auto-crud.include-reads:false}")
    private boolean includeReads;

    @Value("${app.logging.audit.auto-crud.include-path-prefixes:}")
    private String includePathPrefixesRaw;

    @Value("${app.logging.audit.auto-crud.exclude-path-prefixes:}")
    private String excludePathPrefixesRaw;

    private static final ConcurrentHashMap<String, UserIdCacheEntry> USER_ID_CACHE = new ConcurrentHashMap<>();
    private static final long USER_ID_CACHE_TTL_MS = 5 * 60 * 1000L;

    private record UserIdCacheEntry(Long userId, long expiresAtMs) {
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        long startMs = System.currentTimeMillis();
        Throwable error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                boolean shouldWrite = autoCrudEnabled && !AuditLogContextHolder.wasWritten();
                String path = shouldWrite ? safeString(request.getRequestURI(), 512) : null;
                shouldWrite = shouldWrite && path != null && path.startsWith("/api/") && !shouldExcludePath(path);
                String method = shouldWrite ? safeString(request.getMethod(), 16) : null;
                String action = shouldWrite ? mapAction(method, path) : null;
                shouldWrite = shouldWrite && action != null;
                Authentication auth = shouldWrite ? SecurityContextHolder.getContext().getAuthentication() : null;
                String actorName = shouldWrite ? resolveUsername(auth) : null;
                shouldWrite = shouldWrite && actorName != null;
                if (shouldWrite) {
                    Long actorId = resolveUserId(actorName);
                    int status = response.getStatus();
                    AuditResult result = (error == null && status < 400) ? AuditResult.SUCCESS : AuditResult.FAIL;
                    String entityType = deriveEntityType(path);
                    Long entityId = deriveEntityId(request, path);
                    int latencyMs = (int) Math.clamp(System.currentTimeMillis() - startMs, 0L, Integer.MAX_VALUE);

                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("autoCrud", true);
                    details.put("method", method);
                    details.put("path", path);
                    details.put("statusCode", status);
                    details.put("latencyMs", latencyMs);

                    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    if (pattern != null) details.put("pattern", String.valueOf(pattern));
                    Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
                    if (handler != null) details.put("handler", String.valueOf(handler));
                    if (error != null) details.put("error", safeString(error.getClass().getName(), 128));

                    auditLogWriter.write(
                            actorId,
                            actorName,
                            action,
                            entityType,
                            entityId,
                            result,
                            method + " " + path,
                            null,
                            details
                    );
                }
            } catch (Exception ignore) {
                // ignore
            } finally {
                AuditLogContextHolder.clear();
            }
        }
    }

    private boolean shouldExcludePath(String path) {
        if (!matchesIncludePrefixes(path)) return true;

        if (path.startsWith("/api/admin/audit-logs")) return true;
        if (path.startsWith("/api/admin/access-logs")) return true;
        if (path.startsWith("/api/admin/log-retention")) return true;
        if (path.startsWith("/api/auth/")) return true;

        return matchesExcludePrefixes(path);
    }

    private String mapAction(String method, String path) {
        if (method == null) return null;
        String m = method.trim().toUpperCase(Locale.ROOT);
        if (m.isEmpty()) return null;

        if (!includeReads && ("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m))) return null;

        switch (m) {
            case "GET", "HEAD" -> {
                return "CRUD_READ";
            }
            case "POST" -> {
                if (path != null && path.endsWith("/export.csv")) return "CRUD_READ";
                return "CRUD_CREATE";
            }
            case "PUT", "PATCH" -> {
                return "CRUD_UPDATE";
            }
            case "DELETE" -> {
                return "CRUD_DELETE";
            }
        }
        return null;
    }

    private boolean matchesIncludePrefixes(String path) {
        String raw = includePathPrefixesRaw;
        if (raw == null || raw.isBlank()) {
            if (!includeReads) return true;
            return path.startsWith("/api/admin/");
        }

        for (String p : splitPrefixes(raw)) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    private boolean matchesExcludePrefixes(String path) {
        String raw = excludePathPrefixesRaw;
        if (raw == null || raw.isBlank()) return false;
        for (String p : splitPrefixes(raw)) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    private static String[] splitPrefixes(String raw) {
        StringTokenizer st = new StringTokenizer(raw, ",");
        String[] tmp = new String[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            String t = st.nextToken().trim();
            if (!t.isEmpty()) {
                tmp[i++] = t;
            }
        }
        if (i == tmp.length) return tmp;
        String[] out = new String[i];
        System.arraycopy(tmp, 0, out, 0, i);
        return out;
    }

    private static String deriveEntityType(String path) {
        if (path == null) return "API";
        String p = path;
        if (p.startsWith("/api/")) p = p.substring(5);
        if (p.isEmpty()) return "API";
        if (p.startsWith("/")) p = p.substring(1);

        String[] segs = p.split("/");
        if (segs.length == 0) return "API";

        String a = safeSeg(segs, 0);
        String b = safeSeg(segs, 1);

        String raw;
        if ("admin".equalsIgnoreCase(a) || "portal".equalsIgnoreCase(a)) {
            raw = b == null ? a : (a + "_" + b);
        } else {
            raw = a;
        }
        if (raw == null || raw.isBlank()) raw = "API";
        raw = raw.replace('-', '_').replace('.', '_');
        raw = raw.toUpperCase(Locale.ROOT);
        return safeString(raw, 64);
    }

    private static Long deriveEntityId(HttpServletRequest request, String path) {
        Long fromParam = firstLongParam(request);
        if (fromParam != null) return fromParam;
        if (path == null) return null;
        String[] segs = path.split("/");
        for (int i = segs.length - 1; i >= 0; i--) {
            String s = segs[i];
            if (s == null || s.isBlank()) continue;
            if (isNotDigits(s)) continue;
            try {
                return Long.parseLong(s);
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private static Long firstLongParam(HttpServletRequest request) {
        if (request == null) return null;
        for (String k : new String[]{"entityId", "id", "targetId"}) {
            if (k.isBlank()) continue;
            String v = request.getParameter(k);
            if (v == null) continue;
            String t = v.trim();
            if (t.isEmpty()) continue;
            if (isNotDigits(t)) continue;
            try {
                return Long.parseLong(t);
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private Long resolveUserId(String username) {
        if (username == null) return null;

        long now = System.currentTimeMillis();
        UserIdCacheEntry cached = USER_ID_CACHE.get(username);
        if (cached != null && cached.expiresAtMs() > now) return cached.userId();

        Optional<UsersEntity> user = administratorService.findByUsername(username);
        Long id = user.map(UsersEntity::getId).orElse(null);
        USER_ID_CACHE.put(username, new UserIdCacheEntry(id, now + USER_ID_CACHE_TTL_MS));
        return id;
    }

    private static String resolveUsername(Authentication auth) {
        if (auth == null) return null;
        if (!auth.isAuthenticated()) return null;
        if ("anonymousUser".equals(auth.getPrincipal())) return null;
        String name = auth.getName();
        return name == null || name.isBlank() ? null : name.trim();
    }

    private static boolean isNotDigits(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return true;
        }
        return false;
    }

    private static String safeSeg(String[] segs, int idx) {
        if (segs == null || idx < 0 || idx >= segs.length) return null;
        String s = segs[idx];
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safeString(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }
}
