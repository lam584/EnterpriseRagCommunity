package com.example.EnterpriseRagCommunity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class IpPathRateLimitFilter extends OncePerRequestFilter {

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.security.rate-limit.max-requests-per-window:120}")
    private int maxRequestsPerWindow;

    @Value("${app.security.rate-limit.sensitive-max-requests-per-window:20}")
    private int sensitiveMaxRequestsPerWindow;

    @Value("${app.security.rate-limit.sensitive-path-prefixes:/api/auth,/api/.env,/api/.git,/api/phpinfo.php,/api/info.php,/api/keys,/api/apikey}")
    private String sensitivePathPrefixesRaw;

    @Value("${app.security.rate-limit.cleanup-interval-seconds:120}")
    private int cleanupIntervalSeconds;

    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, CounterBucket> counters = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochSecond = new AtomicLong(0);

    private static final Pattern NUMBER_SEGMENT = Pattern.compile("/\\d+");
    private static final Pattern UUID_SEGMENT = Pattern.compile("/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    public IpPathRateLimitFilter(ClientIpResolver clientIpResolver, ObjectMapper objectMapper) {
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || request == null) {
            filterChain.doFilter(request, response);
            return;
        }
        int safeWindowSeconds = Math.max(1, windowSeconds);
        String normalizedPath = normalizePath(request.getRequestURI());
        int limit = resolveLimit(normalizedPath);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long nowSec = Instant.now().getEpochSecond();
        long windowStart = nowSec - (nowSec % safeWindowSeconds);
        String ip = clientIpResolver.resolveClientIp(request);
        if (ip == null || ip.isBlank()) ip = "unknown";
        String key = ip + "|" + normalizedPath;
        CounterDecision decision = checkAndIncrement(key, windowStart, limit);

        maybeCleanup(nowSec, windowStart, safeWindowSeconds);

        if (!decision.allowed()) {
            long retryAfter = Math.max(1, (windowStart + safeWindowSeconds) - nowSec);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", 429);
            body.put("error", "Too Many Requests");
            body.put("message", "Too Many Requests");
            body.put("path", normalizedPath);
            body.put("retryAfterSeconds", retryAfter);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String normalizedPath) {
        if (normalizedPath == null) return maxRequestsPerWindow;
        List<String> prefixes = parsePrefixes(sensitivePathPrefixesRaw);
        for (String prefix : prefixes) {
            if (normalizedPath.startsWith(prefix)) return Math.max(0, sensitiveMaxRequestsPerWindow);
        }
        return Math.max(0, maxRequestsPerWindow);
    }

    private CounterDecision checkAndIncrement(String key, long windowStart, int limit) {
        CounterBucket bucket = counters.compute(key, (k, old) -> {
            if (old == null || old.windowStart != windowStart) {
                CounterBucket created = new CounterBucket();
                created.windowStart = windowStart;
                created.count = 1;
                created.lastSeenEpochSecond = windowStart;
                return created;
            }
            old.count += 1;
            old.lastSeenEpochSecond = windowStart;
            return old;
        });
        boolean allowed = bucket != null && bucket.count <= limit;
        return new CounterDecision(allowed);
    }

    private void maybeCleanup(long nowSec, long currentWindowStart, int safeWindowSeconds) {
        long safeCleanupInterval = Math.max(10, cleanupIntervalSeconds);
        long next = nextCleanupEpochSecond.get();
        if (next > nowSec) return;
        if (!nextCleanupEpochSecond.compareAndSet(next, nowSec + safeCleanupInterval)) return;
        long expireBefore = currentWindowStart - (safeWindowSeconds * 2L);
        counters.entrySet().removeIf(e -> e.getValue() == null || e.getValue().lastSeenEpochSecond < expireBefore);
    }

    private static List<String> parsePrefixes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("[,\\n\\r\\t]+"))
                .map(IpPathRateLimitFilter::normalizePath)
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private static String normalizePath(String raw) {
        if (raw == null) return "";
        String path = raw.trim().toLowerCase();
        if (path.isBlank()) return "/";
        path = path.replaceAll("/{2,}", "/");
        path = NUMBER_SEGMENT.matcher(path).replaceAll("/{id}");
        path = UUID_SEGMENT.matcher(path).replaceAll("/{id}");
        return path;
    }

    private static final class CounterBucket {
        long windowStart;
        int count;
        long lastSeenEpochSecond;
    }

    private record CounterDecision(boolean allowed) {
    }
}
