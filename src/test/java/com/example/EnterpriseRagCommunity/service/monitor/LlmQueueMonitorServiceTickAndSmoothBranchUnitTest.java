package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmQueueMonitorServiceTickAndSmoothBranchTest {

    @Mock
    LlmCallQueueService llmCallQueueService;

    @Mock
    LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;

    private LlmQueueMonitorService newService() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setKeepCompleted(200);
        return new LlmQueueMonitorService(llmCallQueueService, llmQueueTaskHistoryRepository, props);
    }

    private static LlmCallQueueService.QueueSnapshot snap(
            int runningCount,
            int pendingCount,
            List<LlmCallQueueService.TaskSnapshot> running,
            List<LlmCallQueueService.TaskSnapshot> pending,
            List<LlmCallQueueService.TaskSnapshot> recentCompleted
    ) {
        return new LlmCallQueueService.QueueSnapshot(
                4,
                5000,
                runningCount,
                pendingCount,
                running,
                pending,
                recentCompleted
        );
    }

    private static LlmCallQueueService.TaskSnapshot mockCompleted(long seq, Integer tokensOut, Long durMs, Double tps) {
        LlmCallQueueService.TaskSnapshot t = mock(LlmCallQueueService.TaskSnapshot.class);
        lenient().when(t.getSeq()).thenReturn(seq);
        lenient().when(t.getTokensOut()).thenReturn(tokensOut);
        lenient().when(t.getDurationMs()).thenReturn(durMs);
        lenient().when(t.getTokensPerSec()).thenReturn(tps);
        return t;
    }

    private static LlmCallQueueService.TaskSnapshot mockRunning(Integer tokensOut) {
        LlmCallQueueService.TaskSnapshot t = mock(LlmCallQueueService.TaskSnapshot.class);
        lenient().when(t.getTokensOut()).thenReturn(tokensOut);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Object> samples(LlmQueueMonitorService svc) {
        return (ArrayDeque<Object>) ReflectionTestUtils.getField(svc, "samples");
    }

    private static Object newSample(long tsMs, int queueLen, int running, double tokensPerSec) {
        try {
            Class<?> cls = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmQueueMonitorService$Sample");
            Constructor<?> ctor = cls.getDeclaredConstructor(long.class, int.class, int.class, double.class);
            ctor.setAccessible(true);
            return ctor.newInstance(tsMs, queueLen, running, tokensPerSec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static double sampleTokensPerSec(Object sample) {
        try {
            Method m = sample.getClass().getDeclaredMethod("tokensPerSec");
            return (double) m.invoke(sample);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int sampleQueueLen(Object sample) {
        try {
            Method m = sample.getClass().getDeclaredMethod("queueLen");
            return (int) m.invoke(sample);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int sampleRunning(Object sample) {
        try {
            Method m = sample.getClass().getDeclaredMethod("running");
            return (int) m.invoke(sample);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void tick_shouldReturn_whenTrySnapshotNull_andCachedSnapshotNull() {
        LlmQueueMonitorService svc = newService();
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(null);
        when(llmCallQueueService.cachedSnapshot()).thenReturn(new LlmCallQueueService.CachedQueueSnapshot(null, 0L));

        svc.tick();

        assertEquals(0, samples(svc).size());
    }

    @Test
    void tick_shouldUseCachedSnapshot_whenTrySnapshotNull() {
        LlmQueueMonitorService svc = newService();
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(null);
        when(llmCallQueueService.cachedSnapshot()).thenReturn(new LlmCallQueueService.CachedQueueSnapshot(
                snap(0, 3, List.of(), List.of(), List.of()),
                0L
        ));

        svc.tick();

        assertEquals(1, samples(svc).size());
        Object last = samples(svc).peekLast();
        assertNotNull(last);
        assertEquals(3, sampleQueueLen(last));
        assertEquals(0, sampleRunning(last));
        assertEquals(0.0, sampleTokensPerSec(last), 0.0);
    }

    @Test
    void tick_shouldComputeInstantTpsFromTokensOutAndDuration_andUpdateLastSeen() {
        LlmQueueMonitorService svc = newService();
        LlmCallQueueService.TaskSnapshot t1 = mockCompleted(10, 120, 60L, null);
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(
                snap(0, 0, List.of(), List.of(), Arrays.asList(null, t1))
        );

        svc.tick();

        assertEquals(10L, (long) ReflectionTestUtils.getField(svc, "lastSeenCompletedSeq"));
        Object last = samples(svc).peekLast();
        assertNotNull(last);
        assertEquals(2000.0, sampleTokensPerSec(last), 1e-6);
    }

    @Test
    void tick_shouldComputeInstantTpsFromTokensPerSecAverage_whenTokensMissing() {
        LlmQueueMonitorService svc = newService();
        LlmCallQueueService.TaskSnapshot t1 = mockCompleted(11, null, null, 100.0);
        LlmCallQueueService.TaskSnapshot t2 = mockCompleted(12, 0, 0L, 300.0);
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(
                snap(0, 0, List.of(), List.of(), List.of(t1, t2))
        );

        svc.tick();

        assertEquals(12L, (long) ReflectionTestUtils.getField(svc, "lastSeenCompletedSeq"));
        Object last = samples(svc).peekLast();
        assertNotNull(last);
        assertEquals(200.0, sampleTokensPerSec(last), 1e-9);
    }

    @Test
    void tick_shouldBreakOnSeqNotGreaterThanLastSeen() {
        LlmQueueMonitorService svc = newService();
        ReflectionTestUtils.setField(svc, "lastSeenCompletedSeq", 100L);

        LlmCallQueueService.TaskSnapshot tOld = mockCompleted(100, null, null, 9999.0);
        LlmCallQueueService.TaskSnapshot tNewShouldNotBeCounted = mockCompleted(200, null, null, 9999.0);
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(
                snap(0, 0, List.of(), List.of(), List.of(tOld, tNewShouldNotBeCounted))
        );

        svc.tick();

        assertEquals(100L, (long) ReflectionTestUtils.getField(svc, "lastSeenCompletedSeq"));
        Object last = samples(svc).peekLast();
        assertNotNull(last);
        assertEquals(0.0, sampleTokensPerSec(last), 0.0);
    }

    @Test
    void tick_shouldComputeRunningTps_whenDeltaPositive() {
        LlmQueueMonitorService svc = newService();
        ReflectionTestUtils.setField(svc, "lastRunningTokensOutSum", 100L);
        ReflectionTestUtils.setField(svc, "lastRunningTokensOutAtMs", System.currentTimeMillis() - 1000L);

        LlmCallQueueService.TaskSnapshot running = mockRunning(200);
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(
                snap(1, 0, List.of(running), List.of(), List.of())
        );

        svc.tick();

        Object last = samples(svc).peekLast();
        assertNotNull(last);
        double tps = sampleTokensPerSec(last);
        assertTrue(tps > 0.0 && tps < 200.0, "tps=" + tps);
    }

    @Test
    void tick_shouldTrimSamplesTo3600() {
        LlmQueueMonitorService svc = newService();
        ArrayDeque<Object> q = samples(svc);
        long base = System.currentTimeMillis() - 1_000_000L;
        for (int i = 0; i < 3601; i++) {
            q.addLast(newSample(base + i, 0, 0, 0.0));
        }
        when(llmCallQueueService.trySnapshot(200, 0, 200, 5L)).thenReturn(
                snap(0, 0, List.of(), List.of(), List.of())
        );

        svc.tick();

        assertEquals(3600, q.size());
    }

    @Test
    void smoothTokensPerSec_shouldReturn0_whenLastInvalid() {
        LlmQueueMonitorService svc = newService();
        ReflectionTestUtils.setField(svc, "lastNonZeroTokensPerSec", 0.0);
        ReflectionTestUtils.setField(svc, "lastNonZeroTokensPerSecAtMs", 1000L);

        double out = ReflectionTestUtils.invokeMethod(svc, "smoothTokensPerSec", 0.0, 1, 2000L);

        assertEquals(0.0, out, 0.0);
    }

    @Test
    void smoothTokensPerSec_shouldReturnLastTps_whenAgeNonPositive() {
        LlmQueueMonitorService svc = newService();
        ReflectionTestUtils.setField(svc, "lastNonZeroTokensPerSec", 5.0);
        ReflectionTestUtils.setField(svc, "lastNonZeroTokensPerSecAtMs", 3000L);

        double out = ReflectionTestUtils.invokeMethod(svc, "smoothTokensPerSec", 0.0, 1, 2000L);

        assertEquals(5.0, out, 0.0);
    }

    @Test
    void smoothTokensPerSec_shouldDecayAndApplyThreshold() {
        LlmQueueMonitorService svc = newService();
        ReflectionTestUtils.setField(svc, "lastNonZeroTokensPerSec", 1.0);
        ReflectionTestUtils.setField(svc, "lastNonZeroTokensPerSecAtMs", 1000L);

        double decayed = ReflectionTestUtils.invokeMethod(svc, "smoothTokensPerSec", 0.0, 1, 62_000L);
        assertTrue(decayed > 0.05, "decayed=" + decayed);

        double clipped = ReflectionTestUtils.invokeMethod(svc, "smoothTokensPerSec", 0.0, 1, 243_000L);
        assertEquals(0.0, clipped, 0.0);
    }
}
