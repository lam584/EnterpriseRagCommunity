package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LlmRoutingTelemetryServiceTest {

    @Test
    void shouldRecordAndFilterByTaskType() {
        LlmRoutingTelemetryService svc = new LlmRoutingTelemetryService();
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "CHAT", 1, "t1", "p1", "m1", true, "", "", 10L, "test"));
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "EMBEDDING", 1, "t2", "p1", "e1", true, "", "", 12L, "test"));

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> chat = svc.list("CHAT", 10);
        assertEquals(1, chat.size());
        assertEquals("CHAT", chat.get(0).taskType());

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> all = svc.list(null, 10);
        assertTrue(all.size() >= 2);
    }

    @Test
    void shouldIgnoreNullEventAndClampLimit() {
        LlmRoutingTelemetryService svc = new LlmRoutingTelemetryService();
        svc.record(null);
        assertTrue(svc.list(null, 10).isEmpty());

        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "chat", 1, "t1", "p1", "m1", true, "", "", 10L, "test"));
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "chat", 2, "t2", "p1", "m1", true, "", "", 11L, "test"));

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> lim0 = svc.list(null, 0);
        assertEquals(1, lim0.size());
        assertEquals("t2", lim0.get(0).taskId());
    }

    @Test
    void shouldNormalizeAndFilterTaskTypeIncludingBlankAndNullEventTaskType() {
        LlmRoutingTelemetryService svc = new LlmRoutingTelemetryService();
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", null, 1, "n", "p1", "m1", true, "", "", 10L, "test"));
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "  ", 1, "b", "p1", "m1", true, "", "", 10L, "test"));
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "chat", 1, "c", "p1", "m1", true, "", "", 10L, "test"));
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "EMBEDDING", 1, "e", "p1", "m1", true, "", "", 10L, "test"));

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> chat = svc.list("  CHAT  ", 10001);
        assertEquals(1, chat.size());
        assertEquals("c", chat.get(0).taskId());

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> noFilterByBlank = svc.list("   ", 10);
        assertEquals(4, noFilterByBlank.size());
    }

    @Test
    void shouldStopAtLimitAndKeepMostRecentEntriesOnly() {
        LlmRoutingTelemetryService svc = new LlmRoutingTelemetryService();
        for (int i = 0; i < 20_010; i++) {
            svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                    System.currentTimeMillis(),
                    "ROUTE_OK",
                    i % 2 == 0 ? "CHAT" : "EMBEDDING",
                    i,
                    "t" + i,
                    "p1",
                    "m1",
                    true,
                    "",
                    "",
                    1L,
                    "test"
            ));
        }

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> top5 = svc.list(null, 5);
        assertEquals(5, top5.size());
        assertEquals("t20009", top5.get(0).taskId());
        assertEquals("t20005", top5.get(4).taskId());

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> all = svc.list(null, 50_000);
        assertEquals(10_000, all.size());
        assertEquals("t20009", all.get(0).taskId());
        assertEquals("t10010", all.get(all.size() - 1).taskId());
    }

    @Test
    void shouldSupportSubscribeAndUnsubscribeWithExceptionIsolation() {
        LlmRoutingTelemetryService svc = new LlmRoutingTelemetryService();
        AtomicInteger okCount = new AtomicInteger();
        List<String> ids = new ArrayList<>();
        Runnable unsub1 = svc.subscribe(e -> {
            okCount.incrementAndGet();
            ids.add(e.taskId());
        });
        svc.subscribe(e -> {
            throw new RuntimeException("boom");
        });

        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "CHAT", 1, "t1", "p1", "m1", true, "", "", 10L, "test"));
        assertEquals(1, okCount.get());
        assertEquals(List.of("t1"), ids);

        unsub1.run();
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(System.currentTimeMillis(), "ROUTE_OK", "CHAT", 2, "t2", "p1", "m1", true, "", "", 10L, "test"));
        assertEquals(1, okCount.get());
        assertEquals(List.of("t1"), ids);

        Runnable nullUnsub = svc.subscribe(null);
        nullUnsub.run();
        assertFalse(svc.list(null, 10).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenFilterNeverMatchesAndClampNegativeLimit() {
        LlmRoutingTelemetryService svc = new LlmRoutingTelemetryService();
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                System.currentTimeMillis(),
                "ROUTE_OK",
                "CHAT",
                1,
                "t1",
                "p1",
                "m1",
                true,
                "",
                "",
                7L,
                "test"
        ));
        svc.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                System.currentTimeMillis(),
                "ROUTE_OK",
                null,
                2,
                "t2",
                "p1",
                "m1",
                true,
                "",
                "",
                8L,
                "test"
        ));

        List<LlmRoutingTelemetryService.RoutingDecisionEvent> miss = svc.list("translate", -9);
        assertTrue(miss.isEmpty());
    }
}
