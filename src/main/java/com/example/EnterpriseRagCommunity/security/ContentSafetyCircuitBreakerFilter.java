package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ContentSafetyCircuitBreakerFilter extends OncePerRequestFilter {

    private static final String SESSION_USER_ID_KEY = "auth.userId";

    private final ContentSafetyCircuitBreakerService circuitBreakerService;
    private final ObjectMapper objectMapper;

    public ContentSafetyCircuitBreakerFilter(ContentSafetyCircuitBreakerService circuitBreakerService, ObjectMapper objectMapper) {
        this.circuitBreakerService = circuitBreakerService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;
        return path.startsWith("/api/admin/safety/circuit-breaker");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        ContentSafetyCircuitBreakerConfigDTO cfg = circuitBreakerService.getConfig();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        String entrypoint = detectEntrypoint(path, method);
        if (!scopeMatches(cfg, entrypoint, request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String mode = cfg.getMode() == null ? ContentSafetyCircuitBreakerService.MODE_S1 : cfg.getMode();
        if (ContentSafetyCircuitBreakerService.MODE_S1.equalsIgnoreCase(mode)) {
            if (shouldBlockInS1(entrypoint)) {
                filterChain.doFilter(request, response);
                return;
            }
        } else if (ContentSafetyCircuitBreakerService.MODE_S2.equalsIgnoreCase(mode)) {
            if (!shouldBlockInS2(path)) {
                filterChain.doFilter(request, response);
                return;
            }
        } else if (ContentSafetyCircuitBreakerService.MODE_S3.equalsIgnoreCase(mode)) {
            if (!shouldBlockInS3(path)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String msg = cfg.getMessage();
        circuitBreakerService.addBlockedEvent(entrypoint, path, method, msg);
        writeBlockedResponse(response, path, msg, mode);
    }

    private static boolean shouldBlockInS1(String entrypoint) {
        if (entrypoint == null) return false;
        return Set.of(
                "PORTAL_POST_LIST",
                "PORTAL_POST_DETAIL",
                "PORTAL_SEARCH",
                "PORTAL_CHAT",
                "UPLOADS_STATIC",
                "UPLOADS_API",
                "PORTAL_HOT"
        ).contains(entrypoint);
    }

    private static boolean shouldBlockInS2(String path) {
        if (path == null) return true;
        if (isStaticAssetPath(path)) return false;
        if (path.startsWith("/api/admin")) return false;
        if (path.startsWith("/api/auth")) return false;
        return !path.startsWith("/api/public");
    }

    private static boolean shouldBlockInS3(String path) {
        if (path == null) return true;
        if (isStaticAssetPath(path)) return false;
        if (path.startsWith("/api/admin/safety/circuit-breaker")) return false;
        return !path.startsWith("/api/public");
    }

    private static String detectEntrypoint(String path, String method) {
        if (path == null) return null;

        if (path.startsWith("/api/posts/") && isGet(method)) {
            String tail = path.substring("/api/posts/".length());
            if (tail.matches("\\d+")) return "PORTAL_POST_DETAIL";
            return null;
        }
        if (path.equals("/api/posts") && isGet(method)) return "PORTAL_POST_LIST";
        if (path.equals("/api/hot") && isGet(method)) return "PORTAL_HOT";
        if (path.startsWith("/api/portal/search") && isGet(method)) return "PORTAL_SEARCH";
        if (path.startsWith("/api/ai/chat")) return "PORTAL_CHAT";
        if (path.startsWith("/api/uploads")) return "UPLOADS_API";
        if (path.startsWith("/uploads/") || path.equals("/uploads")) return "UPLOADS_STATIC";
        if (path.startsWith("/api/admin/moderation/queue")) return "ADMIN_MODERATION_QUEUE";
        return null;
    }

    private static boolean scopeMatches(ContentSafetyCircuitBreakerConfigDTO cfg, String entrypoint, HttpServletRequest req) {
        if (cfg == null) return false;
        ContentSafetyCircuitBreakerConfigDTO.Scope scope = cfg.getScope();
        if (scope == null) return true;
        if (Boolean.TRUE.equals(scope.getAll())) return true;

        boolean anySelector = (scope.getEntrypoints() != null && !scope.getEntrypoints().isEmpty())
                || (scope.getPostIds() != null && !scope.getPostIds().isEmpty())
                || (scope.getUserIds() != null && !scope.getUserIds().isEmpty());
        if (!anySelector) return true;

        if (entrypoint != null && scope.getEntrypoints() != null && scope.getEntrypoints().stream().anyMatch(e -> safeEq(e, entrypoint))) {
            return true;
        }

        Long postId = extractPostId(req);
        if (postId != null && scope.getPostIds() != null && scope.getPostIds().contains(postId)) {
            return true;
        }

        Long userId = extractUserId(req);
        return userId != null && scope.getUserIds() != null && scope.getUserIds().contains(userId);
    }

    private static Long extractPostId(HttpServletRequest req) {
        if (req == null) return null;
        String path = req.getRequestURI();
        if (path != null && path.startsWith("/api/posts/")) {
            String tail = path.substring("/api/posts/".length());
            if (tail.matches("\\d+")) {
                try {
                    return Long.parseLong(tail);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        String q = req.getParameter("postId");
        if (q != null && !q.isBlank()) {
            try {
                return Long.parseLong(q.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Long extractUserId(HttpServletRequest req) {
        if (req == null) return null;
        Object v = req.getSession(false) == null ? null : req.getSession(false).getAttribute(SESSION_USER_ID_KEY);
        if (v instanceof Number n) return n.longValue();
        String q = req.getParameter("authorId");
        if (q != null && !q.isBlank()) {
            try {
                return Long.parseLong(q.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void writeBlockedResponse(HttpServletResponse response, String path, String message, String mode) throws IOException {
        response.setStatus(503);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Retry-After", "30");

        boolean api = path != null && path.startsWith("/api/");
        if (!api) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write(message == null ? "" : message);
            return;
        }

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> out = new HashMap<>();
        out.put("success", false);
        out.put("code", "CONTENT_SAFETY_CIRCUIT_BREAKER");
        out.put("message", message);
        out.put("mode", mode);
        response.getWriter().write(objectMapper.writeValueAsString(out));
    }

    private static boolean isStaticAssetPath(String path) {
        if (path == null) return false;
        if (path.equals("/") || path.equals("/index.html")) return true;
        if (path.startsWith("/assets/")) return true;
        if (path.startsWith("/fonts/")) return true;
        if (path.equals("/favicon.ico") || path.equals("/robots.txt") || path.equals("/vite-manifest.json")) return true;
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/") || path.startsWith("/webjars/");
    }

    private static boolean isGet(String method) {
        return method != null && method.toUpperCase(Locale.ROOT).equals("GET");
    }

    private static boolean safeEq(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
