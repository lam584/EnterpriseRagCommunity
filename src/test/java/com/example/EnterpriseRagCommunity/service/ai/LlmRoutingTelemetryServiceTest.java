package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
