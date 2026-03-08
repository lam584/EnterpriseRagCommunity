package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AiRerankServiceUtilityBranchTest {

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    void extractString_should_cover_null_empty_missing_blank_and_trim() throws Exception {
        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, null, "k"));
        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of(), "k"));

        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of("x", "1"), "k"));
        HashMap<String, Object> hasNull = new HashMap<>();
        hasNull.put("k", null);
        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, hasNull, "k"));
        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of("k", "   "), "k"));

        assertEquals("v", invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of("k", " v "), "k"));
        assertEquals("1", invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of("k", 1), "k"));
    }

    @Test
    void isHttpStatus_should_match_codes_in_message() throws Exception {
        assertEquals(false, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{IOException.class, int[].class}, null, new int[]{404}));
        assertEquals(false, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{IOException.class, int[].class}, new IOException((String) null), new int[]{404}));

        assertEquals(true, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{IOException.class, int[].class}, new IOException("Upstream returned HTTP 404: x"), new int[]{404}));
        assertEquals(false, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{IOException.class, int[].class}, new IOException("Upstream returned HTTP 500: x"), new int[]{404}));
        assertEquals(true, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{IOException.class, int[].class}, new IOException("HTTP 415"), new int[]{400, 415, 422}));
    }

    @Test
    void buildRerankUserPrompt_should_cover_topN_and_null_docs_and_escaping() throws Exception {
        String p1 = (String) invokePrivateStatic(AiRerankService.class, "buildRerankUserPrompt", new Class<?>[]{String.class, List.class, Integer.class}, null, List.of("d1"), null);
        assertTrue(p1.contains("\"query\": \"\""));
        assertTrue(!p1.contains("\"topN\""));

        String p2 = (String) invokePrivateStatic(AiRerankService.class, "buildRerankUserPrompt", new Class<?>[]{String.class, List.class, Integer.class}, "q", Arrays.asList(null, "a\tb"), 0);
        assertTrue(!p2.contains("\"topN\""));
        assertTrue(p2.contains("\"documents\""));
        assertTrue(p2.contains("\"\""));
        assertTrue(p2.contains("\\t"));

        String p3 = (String) invokePrivateStatic(AiRerankService.class, "buildRerankUserPrompt", new Class<?>[]{String.class, List.class, Integer.class}, "q", List.of("x"), -1);
        assertTrue(!p3.contains("\"topN\""));

        String p4 = (String) invokePrivateStatic(AiRerankService.class, "buildRerankUserPrompt", new Class<?>[]{String.class, List.class, Integer.class}, "q", List.of("x"), 3);
        assertTrue(p4.contains("\"topN\": 3"));
    }

    @Test
    void objectMapperSafeString_should_escape_special_and_control_chars() throws Exception {
        String s1 = (String) invokePrivateStatic(AiRerankService.class, "objectMapperSafeString", new Class<?>[]{String.class}, (Object) null);
        assertEquals("\"\"", s1);

        String s2 = (String) invokePrivateStatic(AiRerankService.class, "objectMapperSafeString", new Class<?>[]{String.class}, "\"\\\b\f\n\r\t\u0001");
        assertTrue(s2.contains("\\\""));
        assertTrue(s2.contains("\\\\"));
        assertTrue(s2.contains("\\b"));
        assertTrue(s2.contains("\\f"));
        assertTrue(s2.contains("\\n"));
        assertTrue(s2.contains("\\r"));
        assertTrue(s2.contains("\\t"));
        assertTrue(s2.contains("\\u0001"));
    }

    @Test
    void stripCodeFences_and_shrink_should_cover_edge_cases() throws Exception {
        String fenced = (String) invokePrivateStatic(AiRerankService.class, "stripCodeFences", new Class<?>[]{String.class}, "```json\n{\"a\":1}\n```");
        assertEquals("{\"a\":1}", fenced);

        String noNewline = (String) invokePrivateStatic(AiRerankService.class, "stripCodeFences", new Class<?>[]{String.class}, "```json{\"a\":1}```");
        assertEquals("```json{\"a\":1}```", noNewline);

        String sNull = (String) invokePrivateStatic(AiRerankService.class, "shrink", new Class<?>[]{String.class, int.class}, null, 3);
        assertEquals("", sNull);

        String sShort = (String) invokePrivateStatic(AiRerankService.class, "shrink", new Class<?>[]{String.class, int.class}, "  abc  ", 10);
        assertEquals("abc", sShort);

        String sLong = (String) invokePrivateStatic(AiRerankService.class, "shrink", new Class<?>[]{String.class, int.class}, "0123456789", 5);
        assertEquals("01...", sLong);
    }

    @Test
    void parseUsageFromJson_should_cover_null_blank_invalid_and_variants() {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertNull(svc.parseUsageFromJson(null));
        assertNull(svc.parseUsageFromJson("   "));
        assertNull(svc.parseUsageFromJson("{bad"));

        assertNotNull(svc.parseUsageFromJson("{\"usage\":{\"total_tokens\": 3}}"));
        assertNotNull(svc.parseUsageFromJson("{\"usage\":{\"totalTokens\": \"7\"}}"));
        assertNull(svc.parseUsageFromJson("{\"usage\":{\"total_tokens\": \"NaN\"}}"));
    }
}

