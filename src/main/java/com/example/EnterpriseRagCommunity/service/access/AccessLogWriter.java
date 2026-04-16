package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccessLogWriter {

    private final AccessLogsRepository accessLogsRepository;

    @Value("${app.logging.access.async-enabled:false}")
    private boolean asyncEnabled = false;

    @Value("${app.logging.access.async-batch-size:200}")
    private int asyncBatchSize = 200;

    @Value("${app.logging.access.async-queue-capacity:50000}")
    private int asyncQueueCapacity = 50_000;

    @Value("${app.logging.access.async-drop-when-full:true}")
    private boolean asyncDropWhenFull = true;

    private final ConcurrentLinkedQueue<AccessLogsEntity> asyncQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger asyncQueueSize = new AtomicInteger(0);

    public void write(AccessLogsEntity e) {
        if (e == null) return;
        normalizeEntity(e);
        if (!asyncEnabled) {
            accessLogsRepository.save(e);
            return;
        }
        enqueueAsync(e);
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
        write(e);
    }

    @Scheduled(fixedDelayString = "${app.logging.access.async-flush-interval-ms:200}")
    @Transactional
    public void flushAsyncQueue() {
        if (!asyncEnabled) return;
        flushBatch(Math.max(1, asyncBatchSize));
    }

    @PreDestroy
    public void flushBeforeShutdown() {
        if (!asyncEnabled) return;
        int batchSize = Math.max(1, asyncBatchSize);
        while (flushBatch(batchSize) > 0) {
            // drain remaining logs before shutdown
        }
    }

    private void enqueueAsync(AccessLogsEntity e) {
        int capacity = Math.max(1, asyncQueueCapacity);
        int current = asyncQueueSize.incrementAndGet();
        if (current > capacity) {
            asyncQueueSize.decrementAndGet();
            if (asyncDropWhenFull) {
                return;
            }
            accessLogsRepository.save(e);
            return;
        }
        asyncQueue.offer(e);
    }

    private int flushBatch(int batchSize) {
        List<AccessLogsEntity> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            AccessLogsEntity next = asyncQueue.poll();
            if (next == null) break;
            asyncQueueSize.decrementAndGet();
            batch.add(next);
        }
        if (batch.isEmpty()) return 0;
        accessLogsRepository.saveAll(batch);
        return batch.size();
    }

    private static void normalizeEntity(AccessLogsEntity e) {
        if (e.getCreatedAt() == null) e.setCreatedAt(LocalDateTime.now());
        if (e.getMethod() == null || e.getMethod().isBlank()) e.setMethod("UNKNOWN");
        if (e.getPath() == null || e.getPath().isBlank()) e.setPath("/");
    }
}

