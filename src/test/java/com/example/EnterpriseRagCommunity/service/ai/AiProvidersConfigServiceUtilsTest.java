package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AiProvidersConfigServiceUtilsTest {

    @Test
    void toNonBlankReturnsNullForNullOrBlank() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("toNonBlank", Object.class);
        m.setAccessible(true);

        assertNull(m.invoke(null, (Object) null));
        assertNull(m.invoke(null, "   "));
        assertEquals("a", m.invoke(null, " a "));
        assertEquals("123", m.invoke(null, 123));
    }

    @Test
    void positiveOrNullReturnsNullForNonPositive() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("positiveOrNull", Integer.class);
        m.setAccessible(true);

        assertNull(m.invoke(null, (Object) null));
        assertNull(m.invoke(null, 0));
        assertNull(m.invoke(null, -1));
        assertEquals(1, m.invoke(null, 1));
    }

    @Test
    void normalizeHeadersFiltersBlankAndMaskValues() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("normalizeHeaders", Map.class);
        m.setAccessible(true);

        assertEquals(Map.of(), m.invoke(null, (Object) null));
        assertEquals(Map.of(), m.invoke(null, Map.of()));

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("  ", "x");
        raw.put("k1", "  ");
        raw.put("k2", "******");
        raw.put("k3", " v3 ");

        assertEquals(Map.of("k3", "v3"), m.invoke(null, raw));
    }

    @Test
    void maskHeadersMasksValuesAndFiltersBlankKeys() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("maskHeaders", Map.class);
        m.setAccessible(true);

        assertEquals(Map.of(), m.invoke(null, (Object) null));
        assertEquals(Map.of(), m.invoke(null, Map.of()));

        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("  ", "x");
        raw.put("k1", "v1");
        raw.put("k2", null);

        assertEquals(Map.of("k1", "******", "k2", "******"), m.invoke(null, raw));
    }

    @Test
    void hasAnyRealSecretValueReturnsTrueOnlyForNonMaskNonBlank() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("hasAnyRealSecretValue", Map.class);
        m.setAccessible(true);

        assertEquals(false, m.invoke(null, Map.of()));
        assertEquals(false, m.invoke(null, Map.of("k", "  ")));
        assertEquals(false, m.invoke(null, Map.of("k", "******")));
        assertEquals(true, m.invoke(null, Map.of("k", "v")));
    }

    @Test
    void mergeHeadersKeepsOldValuesForMaskAndOverridesForRealValues() throws Exception {
        Method m = AiProvidersConfigService.class.getDeclaredMethod("mergeHeaders", Map.class, Map.class);
        m.setAccessible(true);

        assertEquals(Map.of(), m.invoke(null, Map.of("k", "old"), Map.of()));
        assertEquals(Map.of(), m.invoke(null, Map.of("k", "old"), (Object) null));

        Map<String, String> oldH = new LinkedHashMap<>();
        oldH.put("k1", "old1");
        oldH.put("k2", "old2");

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("k1", "******");
        payload.put("k2", " new2 ");
        payload.put("k3", "  ");
        payload.put("  ", "x");

        assertEquals(Map.of("k1", "old1", "k2", "new2"), m.invoke(null, oldH, payload));
    }
}

