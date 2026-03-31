package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalanceModelDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalancePointDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalanceResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LlmLoadBalanceMonitorService {
    private static final long MIN_RANGE_MS = 60_000L;
    private static final long MAX_RANGE_MS = 7L * 24L * 3600_000L;

    private final LlmLoadBalanceTimeseriesService llmLoadBalanceTimeseriesService;

    @Value("${app.ai.metrics.loadBalance.buckets:24}")
    private int buckets;

    public AdminLlmLoadBalanceResponseDTO query(String range, Integer hours) {
        ParsedRange pr = parseRange(range, hours);
        long nowMs = System.currentTimeMillis();
        long startMs = nowMs - pr.rangeMs;
        long totalMs = Math.max(1L, nowMs - startMs);
        int effectiveBuckets = Math.max(6, Math.min(120, buckets));
        long bucketMs = Math.max(1000L, totalMs / (long) effectiveBuckets);
        int bucketSec = (int) Math.max(1L, bucketMs / 1000L);
        double rangeSeconds = Math.max(1.0, (nowMs - startMs) / 1000.0);

        List<AdminLlmLoadBalanceModelDTO> models = new ArrayList<>();
        LlmLoadBalanceTimeseriesService.QueryResult qr = llmLoadBalanceTimeseriesService.query(startMs, nowMs, effectiveBuckets);
        for (LlmLoadBalanceTimeseriesService.ModelSeries a : qr.models()) {
            AdminLlmLoadBalanceModelDTO m = new AdminLlmLoadBalanceModelDTO();
            m.setProviderId(a.providerId());
            m.setModelName(a.modelName());
            m.setCount(a.count());
            m.setQps(a.count() / rangeSeconds);
            m.setAvgResponseMs(a.avgResponseMs());
            m.setErrorRate(a.count() > 0 ? (a.errorCount() * 1.0) / a.count() : 0.0);
            m.setThrottled429Rate(a.count() > 0 ? (a.throttled429Count() * 1.0) / a.count() : 0.0);
            m.setP95ResponseMs(a.p95ResponseMs());

            List<AdminLlmLoadBalancePointDTO> points = new ArrayList<>(effectiveBuckets);
            for (int i = 0; i < a.points().size(); i++) {
                LlmLoadBalanceTimeseriesService.BucketPoint bp = a.points().get(i);
                AdminLlmLoadBalancePointDTO p = new AdminLlmLoadBalancePointDTO();
                p.setTsMs(bp.tsMs());
                p.setCount(bp.count());
                p.setErrorCount(bp.errorCount());
                p.setThrottled429Count(bp.throttled429Count());
                p.setQps(bp.count() / (bucketSec * 1.0));
                p.setAvgResponseMs(bp.avgResponseMs());
                p.setErrorRate(bp.count() > 0 ? (bp.errorCount() * 1.0) / bp.count() : 0.0);
                p.setThrottled429Rate(bp.count() > 0 ? (bp.throttled429Count() * 1.0) / bp.count() : 0.0);
                p.setP95ResponseMs(bp.p95ResponseMs());
                points.add(p);
            }
            m.setPoints(points);
            models.add(m);
        }

        models.sort((a, b) -> Double.compare(b.getQps(), a.getQps()));

        AdminLlmLoadBalanceResponseDTO out = new AdminLlmLoadBalanceResponseDTO();
        out.setRange(pr.label);
        out.setStartMs(startMs);
        out.setEndMs(nowMs);
        out.setBucketSec(bucketSec);
        out.setModels(models);
        return out;
    }

    private static ParsedRange parseRange(String range, Integer hours) {
        if (hours != null && hours > 0) {
            long h = Math.min((long) hours, MAX_RANGE_MS / 3600_000L);
            return new ParsedRange(h * 3600_000L, h + "h");
        }

        String r = range == null ? "" : range.trim().toLowerCase();
        if (r.isEmpty()) return new ParsedRange(3600_000L, "1h");

        long n = 0L;
        String unit = "h";

        int i = 0;
        while (i < r.length() && Character.isDigit(r.charAt(i))) i++;
        if (i > 0) {
            try {
                n = Long.parseLong(r.substring(0, i));
            } catch (NumberFormatException ignored) {
            }
            unit = r.substring(i).trim();
            if (unit.isEmpty()) unit = "h";
        }

        if (n <= 0L) return new ParsedRange(3600_000L, "1h");

        long ms = toMs(n, unit);
        if (ms <= 0L) ms = 3600_000L;
        if (ms < MIN_RANGE_MS) ms = MIN_RANGE_MS;
        if (ms > MAX_RANGE_MS) ms = MAX_RANGE_MS;
        return new ParsedRange(ms, normalizeLabel(ms, n, unit));
    }

    private static long toMs(long n, String unit) {
        String u = unit == null ? "" : unit.trim().toLowerCase();
        return switch (u) {
            case "ms" -> n;
            case "s" -> n * 1000L;
            case "m" -> n * 60_000L;
            case "h" -> n * 3600_000L;
            case "d" -> n * 24L * 3600_000L;
            case "1h" -> n * 3600_000L;
            case "6h" -> n * 3600_000L;
            case "24h" -> n * 3600_000L;
            default -> -1L;
        };
    }

    private static String normalizeLabel(long ms, long parsedN, String parsedUnit) {
        String u = parsedUnit == null ? "" : parsedUnit.trim().toLowerCase();
        long rawMs = toMs(parsedN, u);
        if (rawMs > 0 && rawMs == ms) {
            if (u.equals("ms") || u.equals("s") || u.equals("m") || u.equals("h") || u.equals("d")) return parsedN + u;
        }

        if (ms % (24L * 3600_000L) == 0) return (ms / (24L * 3600_000L)) + "d";
        if (ms % 3600_000L == 0) return (ms / 3600_000L) + "h";
        if (ms % 60_000L == 0) return (ms / 60_000L) + "m";
        return (ms / 1000L) + "s";
    }

    private static final class ParsedRange {
        private final long rangeMs;
        private final String label;

        private ParsedRange(long rangeMs, String label) {
            this.rangeMs = rangeMs;
            this.label = label;
        }
    }

}
