package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmRoutingServiceRouteTargetBranchTest {

    @Test
    void providerIdAndModelName_returnNull_whenTargetIdIsNull() {
        LlmRoutingService.RouteTarget target = new LlmRoutingService.RouteTarget(null, 7, 3, 2.5);

        assertNull(target.providerId());
        assertNull(target.modelName());
        assertEquals(7, target.weight());
        assertEquals(3, target.priority());
        assertEquals(2.5, target.qps());
        assertNull(target.id());
    }

    @Test
    void providerIdAndModelName_returnValues_whenTargetIdExists() {
        LlmRoutingService.TargetId id = new LlmRoutingService.TargetId("provider-a", "model-x");
        LlmRoutingService.RouteTarget target = new LlmRoutingService.RouteTarget(id, 9, 5, null);

        assertEquals("provider-a", target.providerId());
        assertEquals("model-x", target.modelName());
        assertEquals(9, target.weight());
        assertEquals(5, target.priority());
        assertNull(target.qps());
        assertEquals(id, target.id());
    }
}
