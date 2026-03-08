package com.example.EnterpriseRagCommunity.service.retrieval;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HybridRagRetrievalServiceUtilsBranchTest {

    @Test
    void utils_numbers_and_clamp_coverBranches() throws Exception {
        Method escapeJson = HybridRagRetrievalService.class.getDeclaredMethod("escapeJson", String.class);
        escapeJson.setAccessible(true);
        assertEquals("", (String) escapeJson.invoke(null, new Object[] { null }));
        assertEquals("\\\"\\\\\\n\\r\\t", (String) escapeJson.invoke(null, "\"\\\n\r\t"));

        Method trimTrailingZeros = HybridRagRetrievalService.class.getDeclaredMethod("trimTrailingZeros", double.class);
        trimTrailingZeros.setAccessible(true);
        assertEquals("2", (String) trimTrailingZeros.invoke(null, 2.0));
        assertEquals("2.5", (String) trimTrailingZeros.invoke(null, 2.5));

        Method normalizeMinMax = HybridRagRetrievalService.class.getDeclaredMethod("normalizeMinMax", double.class, double.class, double.class);
        normalizeMinMax.setAccessible(true);
        assertEquals(0.0, (double) normalizeMinMax.invoke(null, 1.0, Double.POSITIVE_INFINITY, 10.0), 1e-9);
        assertEquals(0.0, (double) normalizeMinMax.invoke(null, 1.0, 10.0, 10.0), 1e-9);
        assertEquals(0.0, (double) normalizeMinMax.invoke(null, -10.0, 0.0, 10.0), 1e-9);
        assertEquals(1.0, (double) normalizeMinMax.invoke(null, 100.0, 0.0, 10.0), 1e-9);
        assertEquals(0.5, (double) normalizeMinMax.invoke(null, 5.0, 0.0, 10.0), 1e-9);

        Method clampInt = HybridRagRetrievalService.class.getDeclaredMethod("clampInt", int.class, int.class, int.class);
        clampInt.setAccessible(true);
        assertEquals(1, (int) clampInt.invoke(null, 0, 1, 10));
        assertEquals(10, (int) clampInt.invoke(null, 99, 1, 10));
        assertEquals(7, (int) clampInt.invoke(null, 7, 1, 10));

        Method clampIntObj = HybridRagRetrievalService.class.getDeclaredMethod("clampInt", Integer.class, int.class, int.class);
        clampIntObj.setAccessible(true);
        assertEquals(3, (int) clampIntObj.invoke(null, new Object[] { null, 3, 9 }));
    }

    @Test
    void utils_tokenApprox_and_truncate_coverBranches() throws Exception {
        Method approxTokens = HybridRagRetrievalService.class.getDeclaredMethod("approxTokens", String.class);
        approxTokens.setAccessible(true);
        assertEquals(0, (int) approxTokens.invoke(null, new Object[] { null }));
        assertEquals(0, (int) approxTokens.invoke(null, ""));
        assertEquals(1, (int) approxTokens.invoke(null, "abcd"));
        assertEquals(1, (int) approxTokens.invoke(null, "中"));
        assertTrue((int) approxTokens.invoke(null, "中a") >= 2);

        Method truncateByApproxTokens = HybridRagRetrievalService.class.getDeclaredMethod("truncateByApproxTokens", String.class, int.class);
        truncateByApproxTokens.setAccessible(true);
        assertEquals("", (String) truncateByApproxTokens.invoke(null, new Object[] { null, 10 }));
        assertEquals("", (String) truncateByApproxTokens.invoke(null, "abc", 0));
        assertEquals("abcd", (String) truncateByApproxTokens.invoke(null, "abcd", 1));
        assertEquals("中", (String) truncateByApproxTokens.invoke(null, "中a", 1));
    }
}

