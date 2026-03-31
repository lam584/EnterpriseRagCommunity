package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerEventDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerStatusDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ContentSafetyCircuitBreakerService {
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyCircuitBreakerService.class);

    public static final String KEY_CONFIG_JSON = "content_safety.circuit_breaker.config.json";
    public static final String MODE_S1 = "S1";
    public static final String MODE_S2 = "S2";
    public static final String MODE_S3 = "S3";

    private final AppSettingsService appSettingsService;
    private final ObjectMapper objectMapper;

    private final AtomicReference<ContentSafetyCircuitBreakerConfigDTO> configRef = new AtomicReference<>(defaultConfig());
    private final Object configLock = new Object();

    private volatile Instant updatedAt = Instant.EPOCH;
    private volatile String updatedBy = null;
    private volatile Long updatedByUserId = null;
    private volatile boolean persisted = false;
    private volatile Instant lastPersistAt = null;

    private final Deque<ContentSafetyCircuitBreakerEventDTO> recentEvents = new ArrayDeque<>();
    private final Object eventLock = new Object();

    private final LongAdder blockedTotal = new LongAdder();
    private final Map<String, LongAdder> blockedByEntrypoint = new ConcurrentHashMap<>();
    private final Object blockedWindowLock = new Object();
    private final long[] blockedSecondKeys = new long[120];
    private final long[] blockedSecondCounts = new long[120];

    @PostConstruct
    public void init() {
        reloadFromDbIfPresent();
    }

    public ContentSafetyCircuitBreakerConfigDTO getConfig() {
        return normalize(configRef.get());
    }

    public ContentSafetyCircuitBreakerStatusDTO getStatus(int eventsLimit) {
        ContentSafetyCircuitBreakerStatusDTO out = new ContentSafetyCircuitBreakerStatusDTO();
        out.setConfig(getConfig());
        out.setUpdatedAt(updatedAt);
        out.setUpdatedBy(updatedBy);
        out.setUpdatedByUserId(updatedByUserId);
        out.setPersisted(persisted);
        out.setLastPersistAt(lastPersistAt);
        out.setRuntimeMetrics(buildRuntimeMetrics());
        out.setRecentEvents(getRecentEvents(eventsLimit));
        return out;
    }

    public List<ContentSafetyCircuitBreakerEventDTO> getRecentEvents(int limit) {
        int n = Math.clamp(limit, 0, 200);
        synchronized (eventLock) {
            if (n == 0 || recentEvents.isEmpty()) return List.of();
            List<ContentSafetyCircuitBreakerEventDTO> out = new ArrayList<>(n);
            int i = 0;
            for (ContentSafetyCircuitBreakerEventDTO e : recentEvents) {
                if (e == null) continue;
                out.add(e);
                i++;
                if (i >= n) break;
            }
            return out;
        }
    }

    public void reloadFromDbIfPresent() {
        String raw;
        try {
            raw = appSettingsService.getString(KEY_CONFIG_JSON).orElse(null);
        } catch (Exception e) {
            log.warn("Load circuit breaker config failed. err={}", e.getMessage());
            return;
        }
        if (raw == null || raw.isBlank()) return;

        try {
            ContentSafetyCircuitBreakerConfigDTO parsed = objectMapper.readValue(raw, ContentSafetyCircuitBreakerConfigDTO.class);
            ContentSafetyCircuitBreakerConfigDTO normalized = normalize(parsed);
            configRef.set(normalized);
            persisted = true;
            lastPersistAt = Instant.now();
            addEvent("CONFIG_RELOAD", "从数据库加载熔断配置", Map.of("persisted", true));
        } catch (Exception e) {
            log.warn("Parse circuit breaker config failed. err={}", e.getMessage());
        }
    }

    public ContentSafetyCircuitBreakerStatusDTO update(ContentSafetyCircuitBreakerConfigDTO next, Long actorUserId, String actorName, String reason) {
        ContentSafetyCircuitBreakerConfigDTO normalized = normalize(next);
        Instant now = Instant.now();

        synchronized (configLock) {
            configRef.set(normalized);
            updatedAt = now;
            updatedBy = actorName;
            updatedByUserId = actorUserId;
        }

        boolean persistedOk = false;
        try {
            String json = objectMapper.writeValueAsString(normalized);
            appSettingsService.upsertString(KEY_CONFIG_JSON, json);
            persistedOk = true;
            persisted = true;
            lastPersistAt = now;
        } catch (Exception e) {
            persisted = false;
            log.warn("Persist circuit breaker config failed. err={}", e.getMessage());
        }

        Map<String, Object> details = new HashMap<>();
        details.put("enabled", Boolean.TRUE.equals(normalized.getEnabled()));
        details.put("mode", normalized.getMode());
        details.put("reason", trimToNull(reason));
        details.put("persisted", persistedOk);
        addEvent("CONFIG_UPDATE", "更新熔断配置", details);
        return getStatus(50);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(getConfig().getEnabled());
    }

    public boolean isModeS3WithMysqlIsolation() {
        ContentSafetyCircuitBreakerConfigDTO cfg = getConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) return false;
        if (!MODE_S3.equalsIgnoreCase(safeUpper(cfg.getMode()))) return false;
        return cfg.getDependencyIsolation() != null && Boolean.TRUE.equals(cfg.getDependencyIsolation().getMysql());
    }

    public boolean isModeS3WithElasticsearchIsolation() {
        ContentSafetyCircuitBreakerConfigDTO cfg = getConfig();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) return false;
        if (!MODE_S3.equalsIgnoreCase(safeUpper(cfg.getMode()))) return false;
        return cfg.getDependencyIsolation() != null && Boolean.TRUE.equals(cfg.getDependencyIsolation().getElasticsearch());
    }

    public void addBlockedEvent(String entrypoint, String path, String method, String message) {
        recordBlocked(entrypoint);
        Map<String, Object> details = new HashMap<>();
        details.put("entrypoint", trimToNull(entrypoint));
        details.put("path", trimToNull(path));
        details.put("method", trimToNull(method));
        addEvent("REQUEST_BLOCKED", message == null || message.isBlank() ? "请求被熔断拦截" : message, details);
    }

    public void addEvent(String type, String message, Map<String, Object> details) {
        ContentSafetyCircuitBreakerEventDTO e = new ContentSafetyCircuitBreakerEventDTO();
        e.setAt(Instant.now());
        e.setType(type == null ? "UNKNOWN" : type);
        e.setMessage(message);
        e.setDetails(details == null ? Map.of() : details);
        synchronized (eventLock) {
            recentEvents.addFirst(e);
            while (recentEvents.size() > 200) {
                recentEvents.removeLast();
            }
        }
    }

    private void recordBlocked(String entrypoint) {
        blockedTotal.increment();
        String ep = entrypoint == null ? "UNKNOWN" : entrypoint.trim();
        if (ep.isBlank()) ep = "UNKNOWN";
        blockedByEntrypoint.computeIfAbsent(ep, k -> new LongAdder()).increment();

        long nowSec = System.currentTimeMillis() / 1000L;
        int idx = (int) (nowSec % blockedSecondKeys.length);
        synchronized (blockedWindowLock) {
            if (blockedSecondKeys[idx] != nowSec) {
                blockedSecondKeys[idx] = nowSec;
                blockedSecondCounts[idx] = 0L;
            }
            blockedSecondCounts[idx] += 1L;
        }
    }

    private com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerRuntimeMetricsDTO buildRuntimeMetrics() {
        com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerRuntimeMetricsDTO m =
                new com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerRuntimeMetricsDTO();
        m.setBlockedTotal(blockedTotal.sum());
        m.setBlockedLast60s(countBlockedLast60Seconds());

        Map<String, Long> by = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, LongAdder> e : blockedByEntrypoint.entrySet()) {
            if (e == null || e.getKey() == null || e.getValue() == null) continue;
            by.put(e.getKey(), e.getValue().sum());
        }
        m.setBlockedByEntrypoint(by);
        return m;
    }

    private long countBlockedLast60Seconds() {
        int sec = 60;
        long nowSec = System.currentTimeMillis() / 1000L;
        long minSec = nowSec - (sec - 1L);
        long sum = 0L;
        synchronized (blockedWindowLock) {
            for (int i = 0; i < blockedSecondKeys.length; i++) {
                long k = blockedSecondKeys[i];
                if (k >= minSec && k <= nowSec) {
                    sum += blockedSecondCounts[i];
                }
            }
        }
        return sum;
    }

    public static ContentSafetyCircuitBreakerConfigDTO defaultConfig() {
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        cfg.setMode(MODE_S1);
        cfg.setMessage("临时不可用：系统正在进行内容安全处置，请稍后再试。");
        cfg.setScope(ContentSafetyCircuitBreakerConfigDTO.Scope.defaultAll());
        cfg.setDependencyIsolation(ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation.defaults());
        cfg.setAutoTrigger(ContentSafetyCircuitBreakerConfigDTO.AutoTrigger.defaults());
        return cfg;
    }

    public static ContentSafetyCircuitBreakerConfigDTO normalize(ContentSafetyCircuitBreakerConfigDTO in) {
        ContentSafetyCircuitBreakerConfigDTO base = defaultConfig();
        if (in == null) return base;

        ContentSafetyCircuitBreakerConfigDTO out = new ContentSafetyCircuitBreakerConfigDTO();
        out.setEnabled(Boolean.TRUE.equals(in.getEnabled()));

        String mode = safeUpper(in.getMode());
        if (!Objects.equals(mode, MODE_S1) && !Objects.equals(mode, MODE_S2) && !Objects.equals(mode, MODE_S3)) {
            mode = MODE_S1;
        }
        out.setMode(mode);

        String msg = trimToNull(in.getMessage());
        out.setMessage(msg == null ? base.getMessage() : msg);

        ContentSafetyCircuitBreakerConfigDTO.Scope s = in.getScope();
        if (s == null) {
            out.setScope(ContentSafetyCircuitBreakerConfigDTO.Scope.defaultAll());
        } else {
            ContentSafetyCircuitBreakerConfigDTO.Scope ns = new ContentSafetyCircuitBreakerConfigDTO.Scope();
            ns.setAll(Boolean.TRUE.equals(s.getAll()));
            ns.setUserIds(s.getUserIds() == null ? new ArrayList<>() : new ArrayList<>(s.getUserIds()));
            ns.setPostIds(s.getPostIds() == null ? new ArrayList<>() : new ArrayList<>(s.getPostIds()));
            ns.setEntrypoints(s.getEntrypoints() == null ? new ArrayList<>() : new ArrayList<>(s.getEntrypoints()));
            out.setScope(ns);
        }

        ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation di = in.getDependencyIsolation();
        if (di == null) {
            out.setDependencyIsolation(ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation.defaults());
        } else {
            ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation ndi = new ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation();
            ndi.setMysql(Boolean.TRUE.equals(di.getMysql()));
            ndi.setElasticsearch(Boolean.TRUE.equals(di.getElasticsearch()));
            out.setDependencyIsolation(ndi);
        }

        ContentSafetyCircuitBreakerConfigDTO.AutoTrigger at = in.getAutoTrigger();
        if (at == null) {
            out.setAutoTrigger(ContentSafetyCircuitBreakerConfigDTO.AutoTrigger.defaults());
        } else {
            ContentSafetyCircuitBreakerConfigDTO.AutoTrigger nat = new ContentSafetyCircuitBreakerConfigDTO.AutoTrigger();
            nat.setEnabled(Boolean.TRUE.equals(at.getEnabled()));
            nat.setWindowSeconds(clampInt(at.getWindowSeconds(), 5, 3600, 60));
            nat.setThresholdCount(clampInt(at.getThresholdCount(), 1, 1_000_000, 10));
            nat.setMinConfidence(clampConfidence(at.getMinConfidence()));
            nat.setVerdicts(at.getVerdicts() == null ? List.of("REJECT", "REVIEW") : List.copyOf(at.getVerdicts()));
            String triggerMode = safeUpper(at.getTriggerMode());
            if (!Objects.equals(triggerMode, MODE_S1) && !Objects.equals(triggerMode, MODE_S2) && !Objects.equals(triggerMode, MODE_S3)) {
                triggerMode = MODE_S1;
            }
            nat.setTriggerMode(triggerMode);
            nat.setCoolDownSeconds(clampInt(at.getCoolDownSeconds(), 0, 86400, 300));
            nat.setAutoRecoverSeconds(clampInt(at.getAutoRecoverSeconds(), 0, 7 * 86400, 0));
            out.setAutoTrigger(nat);
        }

        return out;
    }

    private static String safeUpper(String s) {
        return s == null ? null : s.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        if (v == null) return def;
        return Math.clamp(v, min, max);
    }

    private static double clampConfidence(Double v) {
        if (v == null) return 0.90;
        double x = v;
        if (Double.isNaN(x) || Double.isInfinite(x)) return 0.90;
        return Math.clamp(x, 0.0, 1.0);
    }
}
