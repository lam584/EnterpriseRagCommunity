package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccessLogWriter {

    private final AccessLogsRepository accessLogsRepository;

    public void write(AccessLogsEntity e) {
        if (e == null) return;
        if (e.getCreatedAt() == null) e.setCreatedAt(LocalDateTime.now());
        if (e.getMethod() == null || e.getMethod().isBlank()) e.setMethod("UNKNOWN");
        if (e.getPath() == null || e.getPath().isBlank()) e.setPath("/");
        accessLogsRepository.save(e);
    }

    public void write(
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
        AccessLogsEntity e = new AccessLogsEntity();
        e.setTenantId(tenantId);
        e.setUserId(userId);
        e.setUsername(username);
        e.setMethod(method == null ? "UNKNOWN" : method);
        e.setPath(path == null ? "/" : path);
        e.setQueryString(queryString);
        e.setStatusCode(statusCode);
        e.setLatencyMs(latencyMs);
        e.setClientIp(clientIp);
        e.setClientPort(clientPort);
        e.setServerIp(serverIp);
        e.setServerPort(serverPort);
        e.setScheme(scheme);
        e.setHost(host);
        e.setRequestId(requestId);
        e.setTraceId(traceId);
        e.setUserAgent(userAgent);
        e.setReferer(referer);
        e.setDetails(details);
        e.setCreatedAt(LocalDateTime.now());
        accessLogsRepository.save(e);
    }
}

