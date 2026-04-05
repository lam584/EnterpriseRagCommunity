package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmCallQueueServiceNormalizeUsageTest {

    @Test
    void normalizeOpenAiCompatUsage_shouldDelegateAndPreserveBehavior() throws Exception {
        Method m = LlmCallQueueService.class.getDeclaredMethod(
                "normalizeOpenAiCompatUsage",
                Integer.class,
                Integer.class,
                Integer.class
        );
        m.setAccessible(true);

        LlmCallQueueService.UsageMetrics full = (LlmCallQueueService.UsageMetrics) m.invoke(null, 10, 0, 12);
        assertEquals(10, full.promptTokens());
        assertEquals(2, full.completionTokens());
        assertEquals(12, full.totalTokens());

        LlmCallQueueService.UsageMetrics derived = (LlmCallQueueService.UsageMetrics) m.invoke(null, null, 4, 10);
        assertEquals(6, derived.promptTokens());
        assertEquals(4, derived.completionTokens());
        assertEquals(10, derived.totalTokens());

        assertNull(m.invoke(null, null, null, null));
    }
}
