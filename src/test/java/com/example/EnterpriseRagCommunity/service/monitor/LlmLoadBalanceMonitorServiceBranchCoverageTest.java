package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalanceModelDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalancePointDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalanceResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmLoadBalanceMonitorServiceBranchCoverageTest {

    @Mock
    private LlmLoadBalanceTimeseriesService llmLoadBalanceTimeseriesService;

    private LlmLoadBalanceMonitorService newServiceWithBuckets(int buckets) {
        LlmLoadBalanceMonitorService svc = new LlmLoadBalanceMonitorService(llmLoadBalanceTimeseriesService);
        ReflectionTestUtils.setField(svc, "buckets", buckets);
        return svc;
    }

    private static Object invokeParseRange(String range, Integer hours) {
        try {
            Method m = LlmLoadBalanceMonitorService.class.getDeclaredMethod("parseRange", String.class, Integer.class);
            m.setAccessible(true);
            return m.invoke(null, range, hours);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long parsedRangeMs(Object parsedRange) {
        try {
            Field f = parsedRange.getClass().getDeclaredField("rangeMs");
            f.setAccessible(true);
            return (long) f.get(parsedRange);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String parsedLabel(Object parsedRange) {
        try {
            Field f = parsedRange.getClass().getDeclaredField("label");
            f.setAccessible(true);
            return (String) f.get(parsedRange);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long invokeToMs(long n, String unit) {
        try {
            Method m = LlmLoadBalanceMonitorService.class.getDeclaredMethod("toMs", long.class, String.class);
            m.setAccessible(true);
            return (long) m.invoke(null, n, unit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String invokeNormalizeLabel(long ms, long parsedN, String parsedUnit) {
        try {
            Method m = LlmLoadBalanceMonitorService.class.getDeclaredMethod("normalizeLabel", long.class, long.class, String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, ms, parsedN, parsedUnit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static LlmLoadBalanceTimeseriesService.BucketPoint point(long tsMs, long count, long errorCount, long throttled429Count, double avg, double p95) {
        return new LlmLoadBalanceTimeseriesService.BucketPoint(tsMs, count, errorCount, throttled429Count, avg, p95);
    }

    private static LlmLoadBalanceTimeseriesService.ModelSeries model(String provider, String model, long count, long errorCount, long throttled429Count, double avg, double p95, List<LlmLoadBalanceTimeseriesService.BucketPoint> points) {
        return new LlmLoadBalanceTimeseriesService.ModelSeries(provider, model, count, errorCount, throttled429Count, avg, p95, points);
    }

    @Test
    void query_shouldMapSortAndCalculateRates() {
        LlmLoadBalanceMonitorService svc = newServiceWithBuckets(24);
        long now = System.currentTimeMillis();
        LlmLoadBalanceTimeseriesService.QueryResult qr = new LlmLoadBalanceTimeseriesService.QueryResult(
                now,
                now - 3600_000L,
                now,
                60,
                List.of(
                        model("p-low", "m-low", 0L, 0L, 0L, 0.0, 0.0, List.of()),
                        model("p-high", "m-high", 10L, 2L, 1L, 120.0, 200.0, List.of(
                                point(now - 10_000L, 0L, 0L, 0L, 0.0, 0.0),
                                point(now - 5_000L, 5L, 1L, 1L, 80.0, 100.0)
                        ))
                )
        );
        when(llmLoadBalanceTimeseriesService.query(anyLong(), anyLong(), anyInt())).thenReturn(qr);

        AdminLlmLoadBalanceResponseDTO out = svc.query("1h", null);

        assertNotNull(out);
        assertEquals("1h", out.getRange());
        assertNotNull(out.getModels());
        assertEquals(2, out.getModels().size());
        assertEquals("p-high", out.getModels().get(0).getProviderId());
        assertEquals("p-low", out.getModels().get(1).getProviderId());

        AdminLlmLoadBalanceModelDTO high = out.getModels().get(0);
        assertTrue(high.getQps() > 0.0);
        assertEquals(0.2, high.getErrorRate(), 1e-9);
        assertEquals(0.1, high.getThrottled429Rate(), 1e-9);

        List<AdminLlmLoadBalancePointDTO> points = high.getPoints();
        assertEquals(2, points.size());
        assertEquals(0.0, points.get(0).getErrorRate(), 1e-9);
        assertEquals(0.0, points.get(0).getThrottled429Rate(), 1e-9);
        assertEquals(0.2, points.get(1).getErrorRate(), 1e-9);
        assertEquals(0.2, points.get(1).getThrottled429Rate(), 1e-9);
    }

    @Test
    void query_shouldClampBucketsMinAndMax() {
        long now = System.currentTimeMillis();
        when(llmLoadBalanceTimeseriesService.query(anyLong(), anyLong(), anyInt()))
                .thenReturn(new LlmLoadBalanceTimeseriesService.QueryResult(now, now - 3600_000L, now, 0, List.of()));

        LlmLoadBalanceMonitorService minSvc = newServiceWithBuckets(1);
        AdminLlmLoadBalanceResponseDTO minOut = minSvc.query("1h", null);
        assertEquals(600, minOut.getBucketSec());

        LlmLoadBalanceMonitorService maxSvc = newServiceWithBuckets(500);
        AdminLlmLoadBalanceResponseDTO maxOut = maxSvc.query("1h", null);
        assertEquals(30, maxOut.getBucketSec());
    }

    @Test
    void parseRange_shouldCoverHoursBranchAndFallbackBranches() {
        Object byHours = invokeParseRange("bad", 2);
        assertEquals(2L * 3600_000L, parsedRangeMs(byHours));
        assertEquals("2h", parsedLabel(byHours));

        Object byHoursZero = invokeParseRange("bad", 0);
        assertEquals(3600_000L, parsedRangeMs(byHoursZero));
        assertEquals("1h", parsedLabel(byHoursZero));

        Object byHoursClamped = invokeParseRange("bad", Integer.MAX_VALUE);
        assertEquals(168L * 3600_000L, parsedRangeMs(byHoursClamped));
        assertEquals("168h", parsedLabel(byHoursClamped));

        Object byNull = invokeParseRange(null, null);
        assertEquals(3600_000L, parsedRangeMs(byNull));
        assertEquals("1h", parsedLabel(byNull));

        Object byEmpty = invokeParseRange("   ", null);
        assertEquals(3600_000L, parsedRangeMs(byEmpty));
        assertEquals("1h", parsedLabel(byEmpty));

        Object nonDigit = invokeParseRange("abc", null);
        assertEquals(3600_000L, parsedRangeMs(nonDigit));
        assertEquals("1h", parsedLabel(nonDigit));

        Object parseError = invokeParseRange("999999999999999999999999h", null);
        assertEquals(3600_000L, parsedRangeMs(parseError));
        assertEquals("1h", parsedLabel(parseError));

        Object noUnit = invokeParseRange("5", null);
        assertEquals(5L * 3600_000L, parsedRangeMs(noUnit));
        assertEquals("5h", parsedLabel(noUnit));

        Object badUnit = invokeParseRange("5x", null);
        assertEquals(3600_000L, parsedRangeMs(badUnit));
        assertEquals("1h", parsedLabel(badUnit));

        Object lowerClamped = invokeParseRange("1s", null);
        assertEquals(60_000L, parsedRangeMs(lowerClamped));
        assertEquals("1m", parsedLabel(lowerClamped));

        Object upperClamped = invokeParseRange("1000d", null);
        assertEquals(7L * 24L * 3600_000L, parsedRangeMs(upperClamped));
        assertEquals("7d", parsedLabel(upperClamped));
    }

    @Test
    void toMs_andNormalizeLabel_shouldCoverUnitAndFallbackBranches() {
        assertEquals(9L, invokeToMs(9L, "ms"));
        assertEquals(7_000L, invokeToMs(7L, "s"));
        assertEquals(120_000L, invokeToMs(2L, "m"));
        assertEquals(10_800_000L, invokeToMs(3L, "h"));
        assertEquals(172_800_000L, invokeToMs(2L, "d"));
        assertEquals(3_600_000L, invokeToMs(1L, "1h"));
        assertEquals(7_200_000L, invokeToMs(2L, "6h"));
        assertEquals(10_800_000L, invokeToMs(3L, "24h"));
        assertEquals(-1L, invokeToMs(3L, "x"));
        assertEquals(-1L, invokeToMs(3L, null));

        assertEquals("5ms", invokeNormalizeLabel(5L, 5L, "ms"));
        assertEquals("5s", invokeNormalizeLabel(5_000L, 5L, "s"));
        assertEquals("5m", invokeNormalizeLabel(5L * 60_000L, 5L, "m"));
        assertEquals("5h", invokeNormalizeLabel(5L * 3600_000L, 5L, "h"));
        assertEquals("5d", invokeNormalizeLabel(5L * 24L * 3600_000L, 5L, "d"));
        assertEquals("1h", invokeNormalizeLabel(3600_000L, 1L, null));
        assertEquals("1h", invokeNormalizeLabel(3600_000L, 1L, "1h"));
        assertEquals("2d", invokeNormalizeLabel(2L * 24L * 3600_000L, 1L, "x"));
        assertEquals("2h", invokeNormalizeLabel(2L * 3600_000L, 1L, "x"));
        assertEquals("2m", invokeNormalizeLabel(2L * 60_000L, 1L, "x"));
        assertEquals("59s", invokeNormalizeLabel(59_000L, 1L, "x"));
    }
}
