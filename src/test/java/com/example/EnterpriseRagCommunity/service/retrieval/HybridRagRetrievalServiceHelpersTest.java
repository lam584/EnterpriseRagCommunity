package com.example.EnterpriseRagCommunity.service.retrieval;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRagRetrievalServiceHelpersTest {

    @Test
    void escapeJson_shouldHandleNullAndSpecialChars() throws Exception {
        assertEquals("", invokeEscapeJson(null));
        assertEquals("abc", invokeEscapeJson("abc"));
        assertEquals("a\\\\b\\\"c\\nd\\r\\te", invokeEscapeJson("a\\b\"c\nd\r\te"));
        assertEquals("\\\\\\\"", invokeEscapeJson("\\\""));
    }

    @Test
    void trimTrailingZeros_shouldTrimAndKeepSpecialValues() throws Exception {
        assertEquals("NaN", invokeTrimTrailingZeros(Double.NaN));
        assertEquals("1.23", invokeTrimTrailingZeros(1.2300));
        assertEquals("1", invokeTrimTrailingZeros(1.000));
        assertEquals("1.234", invokeTrimTrailingZeros(1.234));
    }

    @Test
    void normalizeMinMax_shouldHandleInvalidRangesAndClamp() throws Exception {
        assertEquals(0.0, invokeNormalizeMinMax(0.5, Double.POSITIVE_INFINITY, 1.0), 1e-12);
        assertEquals(0.0, invokeNormalizeMinMax(0.5, 0.0, Double.NaN), 1e-12);
        assertEquals(0.0, invokeNormalizeMinMax(0.5, 1.0, 1.0), 1e-12);
        assertEquals(0.0, invokeNormalizeMinMax(0.0, 1.0, 2.0), 1e-12);
        assertEquals(1.0, invokeNormalizeMinMax(3.0, 1.0, 2.0), 1e-12);
        assertEquals(0.5, invokeNormalizeMinMax(1.5, 1.0, 2.0), 1e-12);
    }

    @Test
    void clampInt_shouldClampPrimitivesAndBoxed() throws Exception {
        assertEquals(0, invokeClampIntPrimitive(-1, 0, 10));
        assertEquals(10, invokeClampIntPrimitive(11, 0, 10));
        assertEquals(5, invokeClampIntPrimitive(5, 0, 10));
        assertEquals(7, invokeClampIntBoxed(7, 1, 9));
        assertEquals(1, invokeClampIntBoxed(null, 1, 9));
    }

    @Test
    void approxTokens_shouldCountAsciiAndNonAscii() throws Exception {
        assertEquals(0, invokeApproxTokens(null));
        assertEquals(0, invokeApproxTokens(""));
        assertEquals(1, invokeApproxTokens("a"));
        assertEquals(1, invokeApproxTokens("aaaa"));
        assertEquals(2, invokeApproxTokens("aaaaa"));
        assertEquals(1, invokeApproxTokens("中"));
        assertEquals(2, invokeApproxTokens("a中"));
    }

    @Test
    void truncateByApproxTokens_shouldTruncateAndHandleEdges() throws Exception {
        assertEquals("", invokeTruncateByApproxTokens(null, 10));
        assertEquals("", invokeTruncateByApproxTokens("abc", 0));
        assertEquals("aaaa", invokeTruncateByApproxTokens("aaaaa", 1));
        assertEquals("中", invokeTruncateByApproxTokens("中a", 1));
        assertEquals("a中b", invokeTruncateByApproxTokens("a中b", 100));
    }

    @Test
    void buildDebugInfo_shouldIncludeAllKeys() throws Exception {
        HybridRagRetrievalService.RetrieveResult r = new HybridRagRetrievalService.RetrieveResult();
        r.setBm25LatencyMs(1);
        r.setVecLatencyMs(2);
        r.setFuseLatencyMs(3);
        r.setRerankLatencyMs(4);
        r.setBm25Error("e1");
        r.setVecError("e2");
        r.setRerankError("e3");

        Map<?, ?> out = invokeBuildDebugInfo(r);
        assertNotNull(out);
        assertEquals(1, out.get("bm25LatencyMs"));
        assertEquals(2, out.get("vecLatencyMs"));
        assertEquals(3, out.get("fuseLatencyMs"));
        assertEquals(4, out.get("rerankLatencyMs"));
        assertEquals("e1", out.get("bm25Error"));
        assertEquals("e2", out.get("vecError"));
        assertEquals("e3", out.get("rerankError"));
    }

    private static String invokeEscapeJson(String s) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("escapeJson", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s);
    }

    private static String invokeTrimTrailingZeros(double v) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("trimTrailingZeros", double.class);
        m.setAccessible(true);
        return (String) m.invoke(null, v);
    }

    private static double invokeNormalizeMinMax(double v, double min, double max) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("normalizeMinMax", double.class, double.class, double.class);
        m.setAccessible(true);
        return (double) m.invoke(null, v, min, max);
    }

    private static int invokeClampIntPrimitive(int v, int min, int max) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("clampInt", int.class, int.class, int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, v, min, max);
    }

    private static int invokeClampIntBoxed(Integer v, int min, int max) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("clampInt", Integer.class, int.class, int.class);
        m.setAccessible(true);
        return (int) m.invoke(null, v, min, max);
    }

    private static int invokeApproxTokens(String s) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("approxTokens", String.class);
        m.setAccessible(true);
        return (int) m.invoke(null, s);
    }

    private static String invokeTruncateByApproxTokens(String s, int maxTokens) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("truncateByApproxTokens", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s, maxTokens);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> invokeBuildDebugInfo(HybridRagRetrievalService.RetrieveResult r) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("buildDebugInfo", HybridRagRetrievalService.RetrieveResult.class);
        m.setAccessible(true);
        Object out = m.invoke(null, r);
        assertTrue(out instanceof Map);
        return (Map<?, ?>) out;
    }
}
