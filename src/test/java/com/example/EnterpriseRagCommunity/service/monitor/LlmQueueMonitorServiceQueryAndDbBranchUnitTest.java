package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueSampleDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmQueueMonitorServiceQueryAndDbBranchTest {

    @Mock
    LlmCallQueueService llmCallQueueService;

    @Mock
    LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;

    @Captor
    ArgumentCaptor<Pageable> pageableCaptor;

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

    private static LlmCallQueueService.TaskSnapshot mockTaskForMerge(String id, long seq, LocalDateTime finishedAt) {
        LlmCallQueueService.TaskSnapshot t = mock(LlmCallQueueService.TaskSnapshot.class);
        when(t.getId()).thenReturn(id);
        when(t.getSeq()).thenReturn(seq);
        when(t.finishedAt()).thenReturn(finishedAt);
        return t;
    }

    private static LlmQueueTaskHistoryEntity entity(String taskId, long seq, LocalDateTime finishedAt) {
        LlmQueueTaskHistoryEntity e = new LlmQueueTaskHistoryEntity();
        e.setTaskId(taskId);
        e.setSeq(seq);
        e.setFinishedAt(finishedAt);
        return e;
    }

    private static Object newCache(long atMs, int limit, List<AdminLlmQueueTaskDTO> list) {
        try {
            Class<?> cls = Class.forName("com.example.EnterpriseRagCommunity.service.monitor.LlmQueueMonitorService$DbRecentCompletedCache");
            Constructor<?> ctor = cls.getDeclaredConstructor(long.class, int.class, List.class);
            ctor.setAccessible(true);
            return ctor.newInstance(atMs, limit, list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private LlmQueueMonitorService newService(LlmQueueProperties props) {
        return new LlmQueueMonitorService(llmCallQueueService, llmQueueTaskHistoryRepository, props);
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Object> samples(LlmQueueMonitorService svc) {
        return (ArrayDeque<Object>) ReflectionTestUtils.getField(svc, "samples");
    }

    private static AdminLlmQueueTaskDTO dto(String id) {
        AdminLlmQueueTaskDTO d = new AdminLlmQueueTaskDTO();
        d.setId(id);
        return d;
    }

    @Test
    void query_shouldFallbackDoneLimitTo200_whenPropsNonPositive() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setKeepCompleted(0);
        LlmQueueMonitorService svc = newService(props);

        when(llmCallQueueService.trySnapshot(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(
                snap(0, 0, null, null, null)
        );
        when(llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class))).thenReturn(List.of());

        var out = svc.query(300, 50, 200, null);

        assertNotNull(out);
        assertEquals(false, out.getStale());
        verify(llmQueueTaskHistoryRepository).findByFinishedAtIsNotNullOrderByFinishedAtDesc(pageableCaptor.capture());
        assertEquals(200, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void query_shouldUseCachedSnapshot_andSnapshotAtNull_whenAtMsZero() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setKeepCompleted(1);
        LlmQueueMonitorService svc = newService(props);

        when(llmCallQueueService.trySnapshot(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(null);
        when(llmCallQueueService.cachedSnapshot()).thenReturn(new LlmCallQueueService.CachedQueueSnapshot(
                snap(10, 10, Collections.singletonList((LlmCallQueueService.TaskSnapshot) null), List.of(), Collections.singletonList((LlmCallQueueService.TaskSnapshot) null)),
                0L
        ));

        var out = svc.query(300, 1, 1, 10);

        assertNotNull(out);
        assertEquals(true, out.getStale());
        assertNull(out.getSnapshotAtMs());
        verify(llmQueueTaskHistoryRepository, never()).findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class));
        verify(llmCallQueueService, never()).snapshot(0, 0, 0);
    }

    @Test
    void query_shouldFallbackToSnapshot_whenCachedSnapshotNull() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setKeepCompleted(1);
        LlmQueueMonitorService svc = newService(props);

        when(llmCallQueueService.trySnapshot(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(null);
        when(llmCallQueueService.cachedSnapshot()).thenReturn(null);
        when(llmCallQueueService.snapshot(0, 0, 0)).thenReturn(
                snap(0, 0, List.of(), List.of(), List.of())
        );
        when(llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class))).thenReturn(List.of());

        var out = svc.query(300, 0, 0, 1);

        assertNotNull(out);
        assertEquals(false, out.getStale());
        assertNotNull(out.getSnapshotAtMs());
        verify(llmCallQueueService).snapshot(0, 0, 0);
    }

    @Test
    void query_shouldMarkTruncated_whenInputOverMax_andCountsExceedLimits() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setKeepCompleted(200);
        LlmQueueMonitorService svc = newService(props);

        when(llmCallQueueService.trySnapshot(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(null);
        when(llmCallQueueService.cachedSnapshot()).thenReturn(new LlmCallQueueService.CachedQueueSnapshot(
                snap(10, 10, List.of(), List.of(), List.of()),
                123L
        ));

        var out = svc.query(300, 1, 1, 2001);

        assertNotNull(out);
        assertEquals(true, out.getStale());
        assertEquals(true, out.getTruncated());
        verify(llmQueueTaskHistoryRepository, never()).findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class));
        verify(llmCallQueueService, never()).snapshot(0, 0, 0);
    }

    @Test
    void query_shouldFilterSamplesOutsideWindow() {
        LlmQueueProperties props = new LlmQueueProperties();
        props.setKeepCompleted(1);
        LlmQueueMonitorService svc = newService(props);

        long now = System.currentTimeMillis();
        samples(svc).addLast(newSample(now - 20_000L, 1, 0, 0.0));
        samples(svc).addLast(newSample(now - 1_000L, 2, 1, 3.0));

        when(llmCallQueueService.trySnapshot(anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(
                snap(0, 0, List.of(), List.of(), List.of())
        );

        var out = svc.query(10, 0, 0, 1);

        assertNotNull(out);
        List<AdminLlmQueueSampleDTO> outSamples = out.getSamples();
        assertNotNull(outSamples);
        assertEquals(1, outSamples.size());
        assertNotNull(outSamples.get(0).getTs());
    }

    @Test
    void mergeRecentCompleted_shouldSortAndTruncateInMemoryOverLimit() {
        LlmQueueProperties props = new LlmQueueProperties();
        LlmQueueMonitorService svc = newService(props);

        LocalDateTime t20250102 = LocalDateTime.parse("2025-01-02T00:00:00");
        LocalDateTime t20250101 = LocalDateTime.parse("2025-01-01T00:00:00");
        LocalDateTime t20241231 = LocalDateTime.parse("2024-12-31T00:00:00");

        List<LlmCallQueueService.TaskSnapshot> inMemory = List.of(
                mockTaskForMerge("t1", 1, t20250102),
                mockTaskForMerge("t2", 9, t20250102),
                mockTaskForMerge("t3", 100, t20250101),
                mockTaskForMerge("t4", 999, null),
                mockTaskForMerge("t5", 0, t20241231)
        );

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> out = ReflectionTestUtils.invokeMethod(svc, "mergeRecentCompleted", inMemory, 3);

        assertNotNull(out);
        assertEquals(3, out.size());
        assertEquals("t2", out.get(0).getId());
        assertEquals("t1", out.get(1).getId());
        assertEquals("t3", out.get(2).getId());
        verify(llmQueueTaskHistoryRepository, never()).findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class));
    }

    @Test
    void mergeRecentCompleted_shouldBreakWhenReachedLimit() {
        LlmQueueProperties props = new LlmQueueProperties();
        LlmQueueMonitorService svc = newService(props);

        List<LlmCallQueueService.TaskSnapshot> inMemory = List.of(mockTaskForMerge("A", 1, LocalDateTime.parse("2025-01-01T00:00:00")));
        when(llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class))).thenReturn(
                List.of(entity("B", 2, LocalDateTime.parse("2025-01-02T00:00:00")))
        );

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> out = ReflectionTestUtils.invokeMethod(svc, "mergeRecentCompleted", inMemory, 2);

        assertNotNull(out);
        assertEquals(2, out.size());
        assertTrue(out.stream().anyMatch(x -> "A".equals(x.getId())));
        assertTrue(out.stream().anyMatch(x -> "B".equals(x.getId())));
    }

    @Test
    void mergeRecentCompleted_shouldNotOverwriteInMemory_whenDbHasSameId() {
        LlmQueueProperties props = new LlmQueueProperties();
        LlmQueueMonitorService svc = newService(props);

        List<LlmCallQueueService.TaskSnapshot> inMemory = List.of(mockTaskForMerge("A", 100, LocalDateTime.parse("2025-01-01T00:00:00")));
        when(llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class))).thenReturn(
                List.of(entity("A", 999, LocalDateTime.parse("2030-01-01T00:00:00")))
        );

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> out = ReflectionTestUtils.invokeMethod(svc, "mergeRecentCompleted", inMemory, 2);

        assertNotNull(out);
        assertEquals(1, out.size());
        assertEquals("A", out.get(0).getId());
        assertEquals(100L, out.get(0).getSeq());
    }

    @Test
    void loadDbRecentCompletedCached_shouldHitCacheWithinTtl_andReturnListOrSubList() {
        LlmQueueProperties props = new LlmQueueProperties();
        LlmQueueMonitorService svc = newService(props);

        long now = System.currentTimeMillis();
        ReflectionTestUtils.setField(svc, "dbRecentCompletedCache", newCache(now - 1L, 3, null));

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> nullList = ReflectionTestUtils.invokeMethod(svc, "loadDbRecentCompletedCached", 1);
        assertNotNull(nullList);
        assertTrue(nullList.isEmpty());

        List<AdminLlmQueueTaskDTO> cached = List.of(dto("a"), dto("b"), dto("c"));
        ReflectionTestUtils.setField(svc, "dbRecentCompletedCache", newCache(now - 1L, 3, cached));

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> same = ReflectionTestUtils.invokeMethod(svc, "loadDbRecentCompletedCached", 3);
        assertSame(cached, same);

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> sub = ReflectionTestUtils.invokeMethod(svc, "loadDbRecentCompletedCached", 2);
        assertEquals(2, sub.size());
        assertEquals("a", sub.get(0).getId());

        verify(llmQueueTaskHistoryRepository, never()).findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class));
    }

    @Test
    void loadDbRecentCompletedCached_shouldQueryDbWhenCacheExpired_andFilterNullEntities_andSubList() {
        LlmQueueProperties props = new LlmQueueProperties();
        LlmQueueMonitorService svc = newService(props);

        long now = System.currentTimeMillis();
        ReflectionTestUtils.setField(svc, "dbRecentCompletedCache", newCache(now - 20_000L, 10, List.of(dto("x"))));

        LlmQueueTaskHistoryEntity bad1 = null;
        LlmQueueTaskHistoryEntity bad2 = new LlmQueueTaskHistoryEntity();
        bad2.setTaskId(null);
        when(llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class))).thenReturn(
                Arrays.asList(
                        bad1,
                        bad2,
                        entity("t1", 1, LocalDateTime.parse("2025-01-01T00:00:00")),
                        entity("t2", 2, LocalDateTime.parse("2025-01-02T00:00:00")),
                        entity("t3", 3, LocalDateTime.parse("2025-01-03T00:00:00")),
                        entity("t4", 4, LocalDateTime.parse("2025-01-04T00:00:00"))
                )
        );

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> out = ReflectionTestUtils.invokeMethod(svc, "loadDbRecentCompletedCached", 2);

        assertNotNull(out);
        assertEquals(2, out.size());

        Object cacheObj = ReflectionTestUtils.getField(svc, "dbRecentCompletedCache");
        assertNotNull(cacheObj);
        try {
            int lim = (int) cacheObj.getClass().getDeclaredMethod("limit").invoke(cacheObj);
            List<?> list = (List<?>) cacheObj.getClass().getDeclaredMethod("list").invoke(cacheObj);
            assertEquals(2, lim);
            assertEquals(4, list.size());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void loadDbRecentCompletedCached_shouldCacheEmpty_whenDbEmpty() {
        LlmQueueProperties props = new LlmQueueProperties();
        LlmQueueMonitorService svc = newService(props);

        when(llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(any(Pageable.class))).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<AdminLlmQueueTaskDTO> out = ReflectionTestUtils.invokeMethod(svc, "loadDbRecentCompletedCached", 5);

        assertNotNull(out);
        assertTrue(out.isEmpty());

        Object cacheObj = ReflectionTestUtils.getField(svc, "dbRecentCompletedCache");
        assertNotNull(cacheObj);
        try {
            List<?> list = (List<?>) cacheObj.getClass().getDeclaredMethod("list").invoke(cacheObj);
            assertNotNull(list);
            assertTrue(list.isEmpty());
        } catch (Exception e) {
            fail(e);
        }
    }
}
