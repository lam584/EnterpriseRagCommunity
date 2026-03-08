package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DependencyCircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(DependencyCircuitBreakerService.class);

    private final AppSettingsService appSettingsService;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    public <T> T run(String dependency, Supplier<T> call) {
        String dep = normalizeDep(dependency);
        if (dep == null) return call.get();

        State s = states.computeIfAbsent(dep, k -> new State());
        long nowMs = System.currentTimeMillis();
        if (s.openUntilMs > nowMs) {
            throw new UpstreamRequestException(HttpStatus.SERVICE_UNAVAILABLE, dep + " 依赖熔断中，请稍后再试");
        }

        try {
            T out = call.get();
            s.failureCount = 0;
            s.lastSuccessAt = Instant.ofEpochMilli(nowMs);
            return out;
        } catch (UpstreamRequestException e) {
            recordFailure(dep, s, nowMs, e);
            throw e;
        } catch (Exception e) {
            recordFailure(dep, s, nowMs, e);
            throw new UpstreamRequestException(HttpStatus.BAD_GATEWAY, dep + " 调用失败: " + safeMessage(e), e);
        }
    }

    public void recordFailure(String dependency, Exception e) {
        String dep = normalizeDep(dependency);
        if (dep == null) return;
        State s = states.computeIfAbsent(dep, k -> new State());
        recordFailure(dep, s, System.currentTimeMillis(), e);
    }

    public Snapshot snapshot(String dependency) {
        String dep = normalizeDep(dependency);
        State s = dep == null ? null : states.get(dep);
        Snapshot out = new Snapshot();
        out.dependency = dep;
        out.failureCount = s == null ? 0 : s.failureCount;
        out.openUntilMs = s == null ? 0 : s.openUntilMs;
        out.lastFailureAt = s == null ? null : s.lastFailureAt;
        out.lastSuccessAt = s == null ? null : s.lastSuccessAt;
        out.failureThreshold = getFailureThreshold(dep);
        out.cooldownSeconds = getCooldownSeconds(dep);
        return out;
    }

    private void recordFailure(String dep, State s, long nowMs, Exception e) {
        s.failureCount++;
        s.lastFailureAt = Instant.ofEpochMilli(nowMs);

        int threshold = getFailureThreshold(dep);
        int cooldownSeconds = getCooldownSeconds(dep);
        if (threshold > 0 && s.failureCount >= threshold) {
            s.openUntilMs = nowMs + (long) cooldownSeconds * 1000L;
            s.failureCount = 0;
            log.warn("Dependency circuit opened. dep={} cooldownSeconds={}", dep, cooldownSeconds);
        }
    }

    private int getFailureThreshold(String dep) {
        String key = "deps." + dep + ".failureThreshold";
        long v = appSettingsService.getLongOrDefault(key, 5);
        if (v < 0) return 0;
        if (v > 1000) return 1000;
        return (int) v;
    }

    private int getCooldownSeconds(String dep) {
        String key = "deps." + dep + ".cooldownSeconds";
        long v = appSettingsService.getLongOrDefault(key, 30);
        if (v < 0) return 0;
        if (v > 3600) return 3600;
        return (int) v;
    }

    private static String normalizeDep(String dep) {
        if (dep == null) return null;
        String t = dep.trim();
        if (t.isBlank()) return null;
        return t.toUpperCase(Locale.ROOT);
    }

    private static String safeMessage(Exception e) {
        String m = e == null ? null : e.getMessage();
        if (m == null) return "unknown";
        String t = m.trim();
        if (t.isBlank()) return "unknown";
        if (t.length() > 200) return t.substring(0, 200);
        return t;
    }

    private static final class State {
        private int failureCount;
        private long openUntilMs;
        private Instant lastFailureAt;
        private Instant lastSuccessAt;
    }

    public static final class Snapshot {
        public String dependency;
        public int failureCount;
        public long openUntilMs;
        public Instant lastFailureAt;
        public Instant lastSuccessAt;
        public int failureThreshold;
        public int cooldownSeconds;
    }
}
