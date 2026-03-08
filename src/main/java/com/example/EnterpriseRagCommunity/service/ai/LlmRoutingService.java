package com.example.EnterpriseRagCommunity.service.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlmRoutingService {

    private static final String ENV_DEFAULT = "default";

    public enum Strategy {
        WEIGHTED_RR,
        PRIORITY_FALLBACK
    }

    public record Policy(
            Strategy strategy,
            int maxAttempts,
            int failureThreshold,
            int cooldownMs
    ) {}

    public record TargetId(
            String providerId,
            String modelName
    ) {}

    public record RouteTarget(
            TargetId id,
            int weight,
            int priority,
            Double qps
    ) {
        public String providerId() {
            return id == null ? null : id.providerId();
        }

        public String modelName() {
            return id == null ? null : id.modelName();
        }
    }

    private record RouteKey(String env, String taskType, String providerId, String modelName) {}

    private record ProviderModelKey(String providerId, String modelName) {}

    private static final class HealthState {
        int consecutiveFailures;
        long cooldownUntilMs;
    }

    private static final class WeightedState {
        int currentWeight;
    }

    private static final class RateState {
        long lastDispatchAtMs;
        double tokens;
        long lastRefillAtMs;
    }

    public record RuntimeTargetState(
            String taskType,
            String providerId,
            String modelName,
            int weight,
            int priority,
            Double qps,
            int runningCount,
            int consecutiveFailures,
            long cooldownUntilMs,
            int currentWeight,
            long lastDispatchAtMs,
            double rateTokens,
            long lastRefillAtMs
    ) {}

    public record RuntimeSnapshot(
            long checkedAtMs,
            Policy policy,
            List<RuntimeTargetState> items
    ) {}

    private final LlmModelRepository llmModelRepository;
    private final LlmRoutingPolicyRepository llmRoutingPolicyRepository;
    private final LlmCallQueueService llmCallQueueService;

    private final Map<RouteKey, HealthState> health = new ConcurrentHashMap<>();
    private final Map<RouteKey, WeightedState> weighted = new ConcurrentHashMap<>();
    private final Map<String, Object> groupLocks = new ConcurrentHashMap<>();
    private final Map<ProviderModelKey, RateState> rate = new ConcurrentHashMap<>();
    private final Map<ProviderModelKey, Object> rateLocks = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public Policy getPolicy(String taskType) {
        String tt = normalizeTaskType(taskType);
        LlmRoutingPolicyEntity e;
        try {
            e = llmRoutingPolicyRepository.findById(new LlmRoutingPolicyId(ENV_DEFAULT, tt)).orElse(null);
        } catch (DataAccessException ex) {
            return defaultPolicy(tt);
        }
        if (e == null) {
            return defaultPolicy(tt);
        }
        Strategy st = parseStrategy(e.getStrategy());
        int maxAttempts = clampInt(e.getMaxAttempts(), 1, 10, defaultPolicy(tt).maxAttempts());
        int failureThreshold = clampInt(e.getFailureThreshold(), 1, 20, 2);
        int cooldownMs = clampInt(e.getCooldownMs(), 0, 3600_000, 30_000);
        return new Policy(st, maxAttempts, failureThreshold, cooldownMs);
    }

    @Transactional(readOnly = true)
    public Policy getPolicy(LlmQueueTaskType taskType) {
        return getPolicy(normalizeTaskType(taskType));
    }

    @Transactional(readOnly = true)
    public List<RouteTarget> listEnabledTargets(String taskType) {
        String purpose = normalizeTaskType(taskType);
        List<LlmModelEntity> models = llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT, purpose);
        Set<String> imageChatAllowKeys = null;
        if ("IMAGE_MODERATION".equalsIgnoreCase(purpose)) {
            imageChatAllowKeys = new HashSet<>();
            List<LlmModelEntity> imageChat = llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT, "IMAGE_CHAT");
            for (LlmModelEntity e : imageChat) {
                if (e == null) continue;
                String pid = toNonBlank(e.getProviderId());
                String mn = toNonBlank(e.getModelName());
                if (pid == null || mn == null) continue;
                imageChatAllowKeys.add(pid + "|" + mn);
            }
        }
        List<RouteTarget> out = new ArrayList<>();
        for (LlmModelEntity m : models) {
            if (m == null) continue;
            String providerId = toNonBlank(m.getProviderId());
            String modelName = toNonBlank(m.getModelName());
            if (providerId == null || modelName == null) continue;
            if (imageChatAllowKeys != null && !imageChatAllowKeys.contains(providerId + "|" + modelName)) continue;
            out.add(new RouteTarget(
                    new TargetId(providerId, modelName),
                    m.getWeight() == null ? 0 : m.getWeight(),
                    m.getPriority() == null ? 0 : m.getPriority(),
                    m.getQps()
            ));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<RouteTarget> listEnabledTargets(LlmQueueTaskType taskType) {
        return listEnabledTargets(normalizeTaskType(taskType));
    }

    @Transactional(readOnly = true)
    public boolean isEnabledTarget(LlmQueueTaskType taskType, String providerId, String modelName) {
        String purpose = normalizeTaskType(taskType);
        String pid = toNonBlank(providerId);
        String mn = toNonBlank(modelName);
        if (pid == null || mn == null) return false;
        boolean ok = llmModelRepository.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue(ENV_DEFAULT, purpose, pid, mn);
        if (!ok) return false;
        if ("IMAGE_MODERATION".equalsIgnoreCase(purpose)) {
            return llmModelRepository.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue(ENV_DEFAULT, "IMAGE_CHAT", pid, mn);
        }
        return true;
    }

    public RouteTarget pickNext(LlmQueueTaskType taskType, Set<TargetId> exclude) {
        Policy policy = getPolicy(taskType);
        List<RouteTarget> candidates = listEnabledTargets(taskType);
        long nowMs = System.currentTimeMillis();
        candidates.removeIf(t -> isExcludedOrCooling(taskType, t, exclude, nowMs));
        if (candidates.isEmpty()) return null;

        Map<ProviderModelKey, Integer> running = runningByModel();
        List<RouteTarget> eligible = new ArrayList<>(candidates);
        eligible.removeIf(t -> !isEligible(t, running, nowMs, true));
        if (eligible.isEmpty()) {
            eligible = new ArrayList<>(candidates);
            eligible.removeIf(t -> !isEligible(t, running, nowMs, false));
        }
        if (eligible.isEmpty()) eligible = candidates;

        for (int guard = 0; guard < Math.max(1, eligible.size()); guard++) {
            RouteTarget best = (policy.strategy() == Strategy.PRIORITY_FALLBACK)
                    ? pickPriorityFallback(eligible)
                    : pickWeightedRoundRobin(taskType, eligible);
            if (best == null) return null;
            if (reserveRate(best, nowMs)) return best;
            eligible.remove(best);
            if (eligible.isEmpty()) return best;
        }
        return eligible.get(0);
    }

    public RouteTarget pickNextInProvider(LlmQueueTaskType taskType, String providerId, Set<TargetId> exclude) {
        String pid = toNonBlank(providerId);
        if (pid == null) return pickNext(taskType, exclude);

        Policy policy = getPolicy(taskType);
        List<RouteTarget> candidates = listEnabledTargets(taskType);
        candidates.removeIf(t -> t == null || !pid.equals(toNonBlank(t.providerId())));
        long nowMs = System.currentTimeMillis();
        candidates.removeIf(t -> isExcludedOrCooling(taskType, t, exclude, nowMs));
        if (candidates.isEmpty()) return null;

        Map<ProviderModelKey, Integer> running = runningByModel();
        List<RouteTarget> eligible = new ArrayList<>(candidates);
        eligible.removeIf(t -> !isEligible(t, running, nowMs, true));
        if (eligible.isEmpty()) {
            eligible = new ArrayList<>(candidates);
            eligible.removeIf(t -> !isEligible(t, running, nowMs, false));
        }
        if (eligible.isEmpty()) eligible = candidates;

        for (int guard = 0; guard < Math.max(1, eligible.size()); guard++) {
            RouteTarget best = (policy.strategy() == Strategy.PRIORITY_FALLBACK)
                    ? pickPriorityFallback(eligible)
                    : pickWeightedRoundRobin(taskType, eligible);
            if (best == null) return null;
            if (reserveRate(best, nowMs)) return best;
            eligible.remove(best);
            if (eligible.isEmpty()) return best;
        }
        return eligible.get(0);
    }

    public RuntimeSnapshot snapshot(LlmQueueTaskType taskType) {
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.UNKNOWN : taskType;
        long nowMs = System.currentTimeMillis();
        Policy policy = getPolicy(tt);
        List<RouteTarget> targets = listEnabledTargets(tt);
        Map<ProviderModelKey, Integer> running = runningByModel();

        List<RuntimeTargetState> items = new ArrayList<>(targets.size());
        for (RouteTarget t : targets) {
            if (t == null) continue;
            String providerId = toNonBlank(t.providerId());
            String modelName = toNonBlank(t.modelName());
            if (providerId == null || modelName == null) continue;

            RouteKey rk = toKey(tt, t);
            HealthState hs = health.get(rk);
            WeightedState ws = weighted.get(rk);
            ProviderModelKey pmk = new ProviderModelKey(providerId, modelName);
            RateState rs = rate.get(pmk);

            int runningCount = running.getOrDefault(pmk, 0);
            int failures = hs == null ? 0 : hs.consecutiveFailures;
            long cooldownUntil = hs == null ? 0L : hs.cooldownUntilMs;
            int currentWeight = ws == null ? 0 : ws.currentWeight;
            long lastDispatchAt = rs == null ? 0L : rs.lastDispatchAtMs;
            long lastRefillAt = rs == null ? 0L : rs.lastRefillAtMs;
            double tokens = peekTokens(rs, nowMs, t.qps());

            items.add(new RuntimeTargetState(
                    normalizeTaskType(tt),
                    providerId,
                    modelName,
                    t.weight(),
                    t.priority(),
                    t.qps(),
                    runningCount,
                    failures,
                    cooldownUntil,
                    currentWeight,
                    lastDispatchAt,
                    tokens,
                    lastRefillAt
            ));
        }

        items.sort(Comparator
                .comparing((RuntimeTargetState x) -> x.taskType() == null ? "" : x.taskType())
                .thenComparing((RuntimeTargetState x) -> x.providerId() == null ? "" : x.providerId())
                .thenComparing((RuntimeTargetState x) -> x.modelName() == null ? "" : x.modelName())
        );

        return new RuntimeSnapshot(nowMs, policy, items);
    }

    private static double peekTokens(RateState st, long nowMs, Double qps) {
        if (st == null) return 0.0;
        if (qps == null || !(qps > 0.0)) return st.tokens;
        RateState tmp = peekRefill(st, nowMs, qps);
        return tmp.tokens;
    }

    private static RouteTarget pickPriorityFallback(List<RouteTarget> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        candidates.sort(Comparator
                    .comparingInt((RouteTarget t) -> t.priority()).reversed()
                    .thenComparing(Comparator.comparingInt((RouteTarget t) -> t.weight()).reversed())
                    .thenComparing(RouteTarget::providerId)
                    .thenComparing(RouteTarget::modelName)
            );
        return candidates.get(0);
    }

    private RouteTarget pickWeightedRoundRobin(LlmQueueTaskType taskType, List<RouteTarget> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        Object lock = groupLocks.computeIfAbsent(ENV_DEFAULT + "|" + normalizeTaskType(taskType), k -> new Object());
        synchronized (lock) {
            int totalWeight = 0;
            for (RouteTarget t : candidates) {
                totalWeight += Math.max(0, t.weight());
            }
            if (totalWeight <= 0) totalWeight = candidates.size();

            RouteTarget best = null;
            int bestWeight = Integer.MIN_VALUE;

            for (RouteTarget t : candidates) {
                RouteKey k = toKey(taskType, t);
                WeightedState st = weighted.computeIfAbsent(k, kk -> new WeightedState());
                int w = Math.max(0, t.weight());
                if (w <= 0) w = 1;
                st.currentWeight += w;
                if (st.currentWeight > bestWeight) {
                    bestWeight = st.currentWeight;
                    best = t;
                }
            }

            if (best == null) return candidates.get(0);

            RouteKey bk = toKey(taskType, best);
            WeightedState bst = weighted.computeIfAbsent(bk, kk -> new WeightedState());
            bst.currentWeight -= totalWeight;
            return best;
        }
    }

    public void recordSuccess(LlmQueueTaskType taskType, RouteTarget target) {
        if (taskType == null || target == null) return;
        RouteKey k = toKey(taskType, target);
        health.compute(k, (kk, st) -> {
            if (st == null) st = new HealthState();
            st.consecutiveFailures = 0;
            st.cooldownUntilMs = 0;
            return st;
        });
    }

    public void recordFailure(LlmQueueTaskType taskType, RouteTarget target) {
        recordFailure(taskType, target, null);
    }

    public void recordFailure(LlmQueueTaskType taskType, RouteTarget target, String errorCode) {
        if (taskType == null || target == null) return;
        Policy policy = getPolicy(taskType);
        RouteKey k = toKey(taskType, target);
        long now = System.currentTimeMillis();
        String code = toNonBlank(errorCode);
        health.compute(k, (kk, st) -> {
            if (st == null) st = new HealthState();
            st.consecutiveFailures = Math.min(10_000, st.consecutiveFailures + 1);
            boolean throttled = "429".equals(code);
            if (throttled) {
                st.consecutiveFailures = Math.max(st.consecutiveFailures, policy.failureThreshold());
                int cd = Math.max(500, Math.min(policy.cooldownMs(), 15_000));
                st.cooldownUntilMs = Math.max(st.cooldownUntilMs, now + cd);
            } else if (st.consecutiveFailures >= policy.failureThreshold()) {
                st.cooldownUntilMs = Math.max(st.cooldownUntilMs, now + Math.max(0, policy.cooldownMs()));
            }
            return st;
        });
    }

    private boolean isExcludedOrCooling(LlmQueueTaskType taskType, RouteTarget t, Set<TargetId> exclude, long nowMs) {
        if (t == null) return true;
        if (exclude != null && exclude.contains(t.id())) return true;
        RouteKey k = toKey(taskType, t);
        HealthState st = health.get(k);
        return st != null && st.cooldownUntilMs > nowMs;
    }

    private boolean isEligible(RouteTarget t, Map<ProviderModelKey, Integer> running, long nowMs, boolean strictRate) {
        if (t == null) return false;
        String providerId = toNonBlank(t.providerId());
        String modelName = toNonBlank(t.modelName());
        if (providerId == null || modelName == null) return false;

        if (!strictRate) return true;

        Double qps = t.qps();
        if (qps == null || !(qps > 0.0)) return true;

        ProviderModelKey key = new ProviderModelKey(providerId, modelName);
        Object lock = rateLocks.computeIfAbsent(key, _k -> new Object());
        synchronized (lock) {
            RateState st = rate.computeIfAbsent(key, _k -> {
                RateState x = new RateState();
                x.lastDispatchAtMs = 0L;
                x.tokens = 0.0;
                x.lastRefillAtMs = nowMs;
                return x;
            });

            if (qps != null && qps > 0.0) {
                RateState peek = peekRefill(st, nowMs, qps);
                if (peek.tokens < 1.0) return false;
            }
            return true;
        }
    }

    private boolean reserveRate(RouteTarget t, long nowMs) {
        if (t == null) return true;
        String providerId = toNonBlank(t.providerId());
        String modelName = toNonBlank(t.modelName());
        if (providerId == null || modelName == null) return true;
        ProviderModelKey key = new ProviderModelKey(providerId, modelName);

        Double qps = t.qps();
        if (qps == null || !(qps > 0.0)) return true;

        Object lock = rateLocks.computeIfAbsent(key, _k -> new Object());
        synchronized (lock) {
            RateState st = rate.computeIfAbsent(key, _k -> {
                RateState x = new RateState();
                x.lastDispatchAtMs = 0L;
                x.tokens = 0.0;
                x.lastRefillAtMs = nowMs;
                return x;
            });
            if (qps != null && qps > 0.0) {
                refillInPlace(st, nowMs, qps);
                if (st.tokens < 1.0) return false;
                st.tokens -= 1.0;
            }
            st.lastDispatchAtMs = nowMs;
            return true;
        }
    }

    private static RateState peekRefill(RateState st, long nowMs, double qps) {
        RateState tmp = new RateState();
        tmp.lastDispatchAtMs = st.lastDispatchAtMs;
        tmp.tokens = st.tokens;
        tmp.lastRefillAtMs = st.lastRefillAtMs;
        refillInPlace(tmp, nowMs, qps);
        return tmp;
    }

    private static void refillInPlace(RateState st, long nowMs, double qps) {
        long last = st.lastRefillAtMs;
        long dtMs = Math.max(0L, nowMs - last);
        if (dtMs <= 0L) return;
        double add = (dtMs / 1000.0) * qps;
        double cap = Math.max(1.0, qps);
        st.tokens = Math.min(cap, st.tokens + add);
        st.lastRefillAtMs = nowMs;
    }

    private Map<ProviderModelKey, Integer> runningByModel() {
        LlmCallQueueService.QueueSnapshot snap = llmCallQueueService.snapshot(Integer.MAX_VALUE, 0, 0);
        Map<ProviderModelKey, Integer> out = new ConcurrentHashMap<>();
        for (LlmCallQueueService.TaskSnapshot t : snap.running()) {
            if (t == null) continue;
            String pid = toNonBlank(t.getProviderId());
            String model = toNonBlank(t.getModel());
            if (pid == null || model == null) continue;
            ProviderModelKey k = new ProviderModelKey(pid, model);
            out.compute(k, (_k, v) -> v == null ? 1 : v + 1);
        }
        return out;
    }

    private static Strategy parseStrategy(String v) {
        String s = toNonBlank(v);
        if (s == null) return Strategy.WEIGHTED_RR;
        String up = s.trim().toUpperCase(Locale.ROOT);
        for (Strategy x : Strategy.values()) {
            if (x.name().equals(up)) return x;
        }
        return Strategy.WEIGHTED_RR;
    }

    private static Policy defaultPolicy(String taskType) {
        if ("MODERATION".equalsIgnoreCase(taskType) || "TEXT_MODERATION".equalsIgnoreCase(taskType) || "IMAGE_MODERATION".equalsIgnoreCase(taskType)) {
            return new Policy(Strategy.PRIORITY_FALLBACK, 2, 2, 30_000);
        }
        return new Policy(Strategy.WEIGHTED_RR, 2, 2, 30_000);
    }

    private static String normalizeTaskType(String tt) {
        if (tt == null) return "UNKNOWN";
        String up = tt.trim().toUpperCase(Locale.ROOT);
        if ("CHAT".equals(up)) return "TEXT_CHAT";
        return up;
    }

    private static String normalizeTaskType(LlmQueueTaskType tt) {
        if (tt == null) return "UNKNOWN";
        String up = tt.name().trim().toUpperCase(Locale.ROOT);
        if ("CHAT".equals(up)) return "TEXT_CHAT";
        return up;
    }

    private static RouteKey toKey(LlmQueueTaskType tt, RouteTarget t) {
        return new RouteKey(
                ENV_DEFAULT,
                normalizeTaskType(tt),
                Objects.requireNonNullElse(toNonBlank(t.providerId()), ""),
                Objects.requireNonNullElse(toNonBlank(t.modelName()), "")
        );
    }

    private static String toNonBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        if (v == null) return def;
        int x = v;
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    public void resetRuntimeState() {
        health.clear();
        weighted.clear();
        groupLocks.clear();
        rate.clear();
        rateLocks.clear();
    }
}
