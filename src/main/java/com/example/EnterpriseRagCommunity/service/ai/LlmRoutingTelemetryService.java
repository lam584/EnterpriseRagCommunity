package com.example.EnterpriseRagCommunity.service.ai;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Service
public class LlmRoutingTelemetryService {

    public record RoutingDecisionEvent(
            long tsMs,
            String kind,
            String taskType,
            Integer attempt,
            String taskId,
            String providerId,
            String modelName,
            Boolean ok,
            String errorCode,
            String errorMessage,
            Long latencyMs,
            String apiSource
    ) {}

    private static final int DEFAULT_KEEP = 20_000;

    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<RoutingDecisionEvent> events = new ArrayDeque<>();
    private final List<Consumer<RoutingDecisionEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void record(RoutingDecisionEvent e) {
        if (e == null) return;
        lock.lock();
        try {
            events.addFirst(e);
            while (events.size() > DEFAULT_KEEP) events.removeLast();
        } finally {
            lock.unlock();
        }
        for (Consumer<RoutingDecisionEvent> sub : subscribers) {
            try {
                sub.accept(e);
            } catch (Exception ignored) {
            }
        }
    }

    public List<RoutingDecisionEvent> list(String taskType, int limit) {
        String tt = normalizeTaskType(taskType);
        int lim = Math.clamp(limit, 1, 10_000);
        List<RoutingDecisionEvent> out = new ArrayList<>(Math.min(lim, 256));
        lock.lock();
        try {
            for (RoutingDecisionEvent e : events) {
                if (out.size() >= lim) break;
                if (tt != null) {
                    String et = normalizeTaskType(e.taskType());
                    if (et == null || !et.equals(tt)) continue;
                }
                out.add(e);
            }
        } finally {
            lock.unlock();
        }
        return out;
    }

    public Runnable subscribe(Consumer<RoutingDecisionEvent> consumer) {
        if (consumer == null) return () -> {};
        subscribers.add(consumer);
        return () -> subscribers.remove(consumer);
    }

    private static String normalizeTaskType(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toUpperCase(Locale.ROOT);
    }
}
