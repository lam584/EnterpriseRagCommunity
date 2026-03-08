package com.example.EnterpriseRagCommunity.service.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HybridRagRetrievalServiceAssistantParsingBranchTest {

    @Test
    void extractAssistantContent_handlesCommonShapes_andFallbacks() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("extractAssistantContent", String.class);
        m.setAccessible(true);

        assertEquals("", (String) m.invoke(svc, new Object[] { null }));
        assertEquals("Hi", (String) m.invoke(svc, "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}]}"));
        assertEquals("Hi2", (String) m.invoke(svc, "{\"choices\":[{\"text\":\"Hi2\"}]}"));

        String raw = "{\"choices\":[{\"message\":{}}]}";
        assertEquals(raw, (String) m.invoke(svc, raw));

        String bad = "{bad";
        assertEquals(bad, (String) m.invoke(svc, bad));
    }

    @Test
    void parseRankingFromAssistant_handlesNoiseFiltering_andDefaults() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("parseRankingFromAssistant", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<?> out0 = (List<?>) m.invoke(svc, new Object[] { null });
        assertEquals(0, out0.size());

        @SuppressWarnings("unchecked")
        List<?> out1 = (List<?>) m.invoke(svc, "prefix {\"ranked\":[{\"doc_id\":\"A\",\"score\":0.9},{\"doc_id\":\"B\"}]} suffix");
        assertEquals(2, out1.size());
        assertEquals("A", docId(out1.get(0)));
        assertEquals(0.9, score(out1.get(0)), 1e-9);
        assertEquals("B", docId(out1.get(1)));
        assertEquals(0.0, score(out1.get(1)), 1e-9);

        @SuppressWarnings("unchecked")
        List<?> out2 = (List<?>) m.invoke(svc, "{\"ranked\":{\"doc_id\":\"A\"}}");
        assertEquals(0, out2.size());

        @SuppressWarnings("unchecked")
        List<?> out3 = (List<?>) m.invoke(svc, "{\"ranked\":[{\"score\":1.0},{\"doc_id\":\"  \"},{\"doc_id\":\"OK\",\"score\":1.0}]}");
        assertEquals(1, out3.size());
        assertEquals("OK", docId(out3.get(0)));

        @SuppressWarnings("unchecked")
        List<?> out4 = (List<?>) m.invoke(svc, "prefix {\"ranked\":[{\"doc_id\":\"A\"}]");
        assertEquals(0, out4.size());
    }

    private static String docId(Object scoredDoc) throws Exception {
        assertNotNull(scoredDoc);
        Field f = scoredDoc.getClass().getDeclaredField("docId");
        f.setAccessible(true);
        return (String) f.get(scoredDoc);
    }

    private static double score(Object scoredDoc) throws Exception {
        assertNotNull(scoredDoc);
        Field f = scoredDoc.getClass().getDeclaredField("score");
        f.setAccessible(true);
        return (double) f.get(scoredDoc);
    }
}

