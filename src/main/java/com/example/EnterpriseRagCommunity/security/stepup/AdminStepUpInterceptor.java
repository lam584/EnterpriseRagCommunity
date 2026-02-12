package com.example.EnterpriseRagCommunity.security.stepup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class AdminStepUpInterceptor implements HandlerInterceptor {
    public static final String SESSION_KEY_OK_UNTIL_EPOCH_MS = "admin.stepup.okUntilEpochMs";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        RequireAdminStepUp cfg = resolveConfig(handler);
        if (cfg == null) return true;

        HttpSession session = request.getSession(false);
        long now = Instant.now().toEpochMilli();
        long okUntil = readOkUntil(session);
        if (okUntil > now) return true;

        response.setStatus(403);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"ADMIN_STEP_UP_REQUIRED\",\"message\":\"需要二次验证后才能执行高权限操作\",\"methods\":[\"email\",\"totp\"],\"ttlSeconds\":"
                        + Math.max(60, cfg.ttlSeconds()) +
                        "}"
        );
        return false;
    }

    private static long readOkUntil(HttpSession session) {
        if (session == null) return 0L;
        Object raw = session.getAttribute(SESSION_KEY_OK_UNTIL_EPOCH_MS);
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (Exception ignore) {
                return 0L;
            }
        }
        return 0L;
    }

    private static RequireAdminStepUp resolveConfig(Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return null;
        RequireAdminStepUp onMethod = hm.getMethodAnnotation(RequireAdminStepUp.class);
        if (onMethod != null) return onMethod;
        Class<?> type = hm.getBeanType();
        return type.getAnnotation(RequireAdminStepUp.class);
    }
}
