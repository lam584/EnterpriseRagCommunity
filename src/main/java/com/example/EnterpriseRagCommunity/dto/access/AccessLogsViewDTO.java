package com.example.EnterpriseRagCommunity.dto.access;

import java.time.LocalDateTime;
import java.util.Map;

public record AccessLogsViewDTO(
        Long id,
        LocalDateTime createdAt,

        Long tenantId,
        Long userId,
        String username,

        String method,
        String path,
        String queryString,

        Integer statusCode,
        Integer latencyMs,

        String clientIp,
        Integer clientPort,
        String serverIp,
        Integer serverPort,

        String scheme,
        String host,

        String requestId,
        String traceId,

        String userAgent,
        String referer,

        Map<String, Object> details
) {
}

