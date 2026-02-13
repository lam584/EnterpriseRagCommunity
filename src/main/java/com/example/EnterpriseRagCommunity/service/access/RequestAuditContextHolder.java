package com.example.EnterpriseRagCommunity.service.access;

import java.util.Map;

public final class RequestAuditContextHolder {
    private RequestAuditContextHolder() {}

    public record RequestAuditContext(
            String requestId,
            String traceId,
            String clientIp,
            Integer clientPort,
            String serverIp,
            Integer serverPort,
            String method,
            String path,
            String scheme,
            String host,
            String userAgent,
            String referer,
            Map<String, Object> details
    ) {
    }

    private static final ThreadLocal<RequestAuditContext> CTX = new ThreadLocal<>();

    public static void set(RequestAuditContext ctx) {
        if (ctx == null) CTX.remove();
        else CTX.set(ctx);
    }

    public static RequestAuditContext get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}

