package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class LlmLoadBalanceTimeseriesService {

    public record ModelKey(String providerId, String modelName) {
        public String key() {
            return providerId + "|" + modelName;
        }
    }

    public record BucketPoint(
            long tsMs,
            long count,
            long errorCount,
            long throttled429Count,
            double avgResponseMs,
            double p95ResponseMs
    ) {}

    public record ModelSeries(
            String providerId,
            String modelName,
            long count,
            long errorCount,
            long throttled429Count,
            double avgResponseMs,
            double p95ResponseMs,
            List<BucketPoint> points
    ) {}

    public record QueryResult(
            long checkedAtMs,
            long startMs,
            long endMs,
            int bucketSec,
            List<ModelSeries> models
    ) {}

    private static final long MINUTE_MS = 60_000L;
    private static final long KEEP_MS = 7L * 24L * 3600_000L;
    private static final int PER_MINUTE_SAMPLE_KEEP = 10;

    private static final class MinuteAgg {
        long count;
        long sumDurMs;
        long errorCount;
        long throttled429Count;
        long seenSamples;
        final ArrayList<Long> durSamples = new ArrayList<>(PER_MINUTE_SAMPLE_KEEP);
        final Object lock = new Object();

        void add(long durMs, boolean ok, boolean throttled429) {
            count++;
            sumDurMs += Math.max(0L, durMs);
            if (!ok) errorCount++;
            if (throttled429) throttled429Count++;

            long seen = ++seenSamples;
            synchronized (lock) {
                if (durSamples.size() < PER_MINUTE_SAMPLE_KEEP) {
                    durSamples.add(Math.max(0L, durMs));
                    return;
                }
                long j = ThreadLocalRandom.current().nextLong(seen);
                if (j < PER_MINUTE_SAMPLE_KEEP) {
                    durSamples.set((int) j, Math.max(0L, durMs));
                }
            }
        }

        List<Long> copySamples() {
            synchronized (lock) {
                return new ArrayList<>(durSamples);
            }
        }
    }

    private static final class MinuteBucket {
        final ConcurrentHashMap<ModelKey, MinuteAgg> byModel = new ConcurrentHashMap<>();
    }

    private final LlmCallQueueService llmCallQueueService;
    private final ConcurrentHashMap<Long, MinuteBucket> minuteBuckets = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong lastCleanupAtMs = new java.util.concurrent.atomic.AtomicLong(0L);

    public LlmLoadBalanceTimeseriesService(LlmCallQueueService llmCallQueueService) {
        this.llmCallQueueService = llmCallQueueService;
        this.llmCallQueueService.subscribeCompleted(this::onCompleted);
    }

    private void onCompleted(LlmCallQueueService.TaskSnapshot t) {
        if (t == null) return;
        String providerId = trim(t.getProviderId());
        String modelName = trim(t.getModel());
        if (providerId == null || modelName == null) return;

        Long finishedAt = t.getFinishedAtMs();
        long finishedAtMs = finishedAt == null ? System.currentTimeMillis() : finishedAt;
        long minuteStart = (finishedAtMs / MINUTE_MS) * MINUTE_MS;

        long durMs = 0L;
        if (t.getDurationMs() != null) durMs = Math.max(0L, t.getDurationMs());
        else if (t.getStartedAtMs() != null && finishedAt != null) durMs = Math.max(0L, finishedAt - t.getStartedAtMs());

        boolean ok = t.getStatus() != null && t.getStatus() == com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskStatus.DONE;
        boolean throttled429 = !ok && isThrottled429(t.getError());

        MinuteBucket bucket = minuteBuckets.computeIfAbsent(minuteStart, _k -> new MinuteBucket());
        ModelKey key = new ModelKey(providerId, modelName);
        MinuteAgg agg = bucket.byModel.computeIfAbsent(key, _k -> new MinuteAgg());
        agg.add(durMs, ok, throttled429);

        maybeCleanup(finishedAtMs);
    }

    private void maybeCleanup(long nowMs) {
        long last = lastCleanupAtMs.get();
        if (last > 0 && nowMs - last < 60_000L) return;
        if (!lastCleanupAtMs.compareAndSet(last, nowMs)) return;
        long cutoff = nowMs - KEEP_MS;
        for (Long k : minuteBuckets.keySet()) {
            if (k != null && k < cutoff) minuteBuckets.remove(k);
        }
    }

    public QueryResult query(long startMs, long endMs, int buckets) {
        long nowMs = System.currentTimeMillis();
        long start = Math.max(0L, startMs);
        long end = Math.max(start, endMs);
        long totalMs = Math.max(1L, end - start);
        int nBuckets = Math.max(1, Math.min(120, buckets));
        long bucketMs = Math.max(1000L, totalMs / nBuckets);
        int bucketSec = (int) Math.max(1L, bucketMs / 1000L);

        long startMinute = (start / MINUTE_MS) * MINUTE_MS;
        long endMinute = (end / MINUTE_MS) * MINUTE_MS;

        Map<ModelKey, ModelAgg> models = new HashMap<>();
        for (long m = startMinute; m <= endMinute; m += MINUTE_MS) {
            MinuteBucket mb = minuteBuckets.get(m);
            if (mb == null) continue;
            for (Map.Entry<ModelKey, MinuteAgg> e : mb.byModel.entrySet()) {
                if (e == null) continue;
                ModelKey k = e.getKey();
                MinuteAgg a = e.getValue();
                if (k == null || a == null) continue;
                ModelAgg ma = models.computeIfAbsent(k, kk -> new ModelAgg(kk, nBuckets));
                ma.addMinute(m, a, start, bucketMs);
            }
        }

        List<ModelSeries> out = new ArrayList<>(models.size());
        for (ModelAgg ma : models.values()) {
            if (ma == null) continue;
            out.add(ma.toSeries(start, bucketMs));
        }
        out.sort((a, b) -> Long.compare(b.count(), a.count()));
        return new QueryResult(nowMs, start, end, bucketSec, out);
    }

    private static final class BucketAgg {
        long count;
        long sumDurMs;
        long errorCount;
        long throttled429Count;
        final ArrayList<Long> samples = new ArrayList<>();
    }

    private static final class ModelAgg {
        final ModelKey key;
        long count;
        long sumDurMs;
        long errorCount;
        long throttled429Count;
        final BucketAgg[] buckets;
        final ArrayList<Long> samples = new ArrayList<>();

        ModelAgg(ModelKey key, int nBuckets) {
            this.key = key;
            this.buckets = new BucketAgg[nBuckets];
            for (int i = 0; i < nBuckets; i++) buckets[i] = new BucketAgg();
        }

        void addMinute(long minuteStart, MinuteAgg a, long startMs, long bucketMs) {
            long idx = (minuteStart - startMs) / bucketMs;
            int bi = (int) Math.max(0, Math.min(buckets.length - 1, idx));
            BucketAgg b = buckets[bi];

            count += a.count;
            sumDurMs += a.sumDurMs;
            errorCount += a.errorCount;
            throttled429Count += a.throttled429Count;

            b.count += a.count;
            b.sumDurMs += a.sumDurMs;
            b.errorCount += a.errorCount;
            b.throttled429Count += a.throttled429Count;

            List<Long> ss = a.copySamples();
            if (!ss.isEmpty()) {
                b.samples.addAll(ss);
                samples.addAll(ss);
            }
        }

        ModelSeries toSeries(long startMs, long bucketMs) {
            String pid = key == null ? "" : key.providerId();
            String model = key == null ? "" : key.modelName();

            double avgMs = count > 0 ? (sumDurMs * 1.0) / count : 0.0;
            double p95 = percentileMs(samples, 0.95);
            List<BucketPoint> points = new ArrayList<>(buckets.length);
            for (int i = 0; i < buckets.length; i++) {
                BucketAgg b = buckets[i];
                double bAvg = b.count > 0 ? (b.sumDurMs * 1.0) / b.count : 0.0;
                double bP95 = percentileMs(b.samples, 0.95);
                points.add(new BucketPoint(
                        startMs + (bucketMs * i),
                        b.count,
                        b.errorCount,
                        b.throttled429Count,
                        bAvg,
                        bP95
                ));
            }
            return new ModelSeries(pid, model, count, errorCount, throttled429Count, avgMs, p95, points);
        }
    }

    private static double percentileMs(List<Long> samples, double p) {
        if (samples == null || samples.isEmpty()) return 0.0;
        List<Long> list = new ArrayList<>(samples.size());
        for (Long x : samples) {
            if (x == null) continue;
            if (x < 0) continue;
            list.add(x);
        }
        if (list.isEmpty()) return 0.0;
        list.sort(Long::compare);
        double pp = Math.max(0.0, Math.min(1.0, p));
        int idx = (int) Math.ceil(pp * list.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= list.size()) idx = list.size() - 1;
        return list.get(idx);
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isThrottled429(String error) {
        if (error == null) return false;
        String t = error.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        return t.contains("429") || t.contains("too many requests") || t.contains("rate limit");
    }
}
