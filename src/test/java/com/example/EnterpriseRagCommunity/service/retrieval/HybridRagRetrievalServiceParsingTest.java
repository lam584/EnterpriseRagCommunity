package com.example.EnterpriseRagCommunity.service.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRagRetrievalServiceParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseEsHits_shouldReturnEmpty_whenRootNull() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<HybridRagRetrievalService.DocHit> hits = invokeParseEsHits(svc, null);
        assertNotNull(hits);
        assertTrue(hits.isEmpty());
    }

    @Test
    void parseEsHits_shouldParseKnownFields() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null
        );

        JsonNode root = objectMapper.readTree("""
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.5,"_source":{"post_id":10,"chunk_index":1,"board_id":3,"title":"t","content_text":"c"}},
                  {"_id":"d2","_source":{"title":"t2","content_text":"c2"}}
                ]}}
                """);

        List<HybridRagRetrievalService.DocHit> hits = invokeParseEsHits(svc, root);
        assertEquals(2, hits.size());
        assertEquals("d1", hits.get(0).getDocId());
        assertEquals(0.5, hits.get(0).getScore(), 1e-12);
        assertEquals(10L, hits.get(0).getPostId());
        assertEquals(1, hits.get(0).getChunkIndex());
        assertEquals(3L, hits.get(0).getBoardId());
        assertEquals("t", hits.get(0).getTitle());
        assertEquals("c", hits.get(0).getContentText());

        assertEquals("d2", hits.get(1).getDocId());
        assertEquals("t2", hits.get(1).getTitle());
        assertEquals("c2", hits.get(1).getContentText());
    }

    @Test
    void filterVisibleHits_shouldReturnInput_whenNoPostIds() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null
        );

        HybridRagRetrievalService.DocHit h1 = new HybridRagRetrievalService.DocHit();
        h1.setDocId("d1");
        h1.setPostId(null);
        List<HybridRagRetrievalService.DocHit> in = List.of(h1);

        List<HybridRagRetrievalService.DocHit> out = invokeFilterVisibleHits(svc, in);
        assertEquals(in, out);
    }

    @Test
    void extractAssistantContent_shouldSupportMessageContentAndText_andFallback() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("", invokeExtractAssistantContent(svc, null));
        assertEquals("HELLO", invokeExtractAssistantContent(svc, "{\"choices\":[{\"message\":{\"content\":\"HELLO\"}}]}"));
        assertEquals("T1", invokeExtractAssistantContent(svc, "{\"choices\":[{\"text\":\"T1\"}]}"));
        assertEquals("{not json", invokeExtractAssistantContent(svc, "{not json"));
    }

    @Test
    void parseRankingFromAssistant_shouldHandleNoise_invalidRanked_andFiltering() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertTrue(invokeParseRankingFromAssistant(svc, null).isEmpty());

        List<?> one = invokeParseRankingFromAssistant(svc, "xxx {\"ranked\":[{\"doc_id\":\"d1\",\"score\":0.9}]} yyy");
        assertEquals(1, one.size());

        assertTrue(invokeParseRankingFromAssistant(svc, "{\"foo\":1}").isEmpty());
        assertTrue(invokeParseRankingFromAssistant(svc, "{\"ranked\":{}}").isEmpty());

        List<?> filtered = invokeParseRankingFromAssistant(svc, "{\"ranked\":[{\"doc_id\":\"   \",\"score\":1.0},{\"doc_id\":\"d2\"}]}");
        assertEquals(1, filtered.size());
    }

    @Test
    void buildRerankPrompt_shouldIncludeDocs_andOptionalTitle() throws Exception {
        String query = "q";
        Map<String, Object> d1 = new HashMap<>();
        d1.put("doc_id", "d1");
        d1.put("title", "t1");
        d1.put("text", "c1");
        Map<String, Object> d2 = new HashMap<>();
        d2.put("doc_id", "d2");
        d2.put("title", " ");
        d2.put("text", "c2");

        String out = invokeBuildRerankPrompt(query, List.of(d1, d2));
        assertTrue(out.contains("query:\nq"));
        assertTrue(out.contains("- doc_id: d1"));
        assertTrue(out.contains("title: t1"));
        assertTrue(out.contains("- doc_id: d2"));
        assertTrue(out.contains("Output format (Strict JSON, no explanation):"));
    }

    @SuppressWarnings("unchecked")
    private static List<HybridRagRetrievalService.DocHit> invokeParseEsHits(HybridRagRetrievalService svc, JsonNode root) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("parseEsHits", JsonNode.class);
        m.setAccessible(true);
        return (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, root);
    }

    @SuppressWarnings("unchecked")
    private static List<HybridRagRetrievalService.DocHit> invokeFilterVisibleHits(HybridRagRetrievalService svc, List<HybridRagRetrievalService.DocHit> hits) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("filterVisibleHits", List.class);
        m.setAccessible(true);
        return (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, hits);
    }

    private static String invokeExtractAssistantContent(HybridRagRetrievalService svc, String rawJson) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("extractAssistantContent", String.class);
        m.setAccessible(true);
        return (String) m.invoke(svc, rawJson);
    }

    private static List<?> invokeParseRankingFromAssistant(HybridRagRetrievalService svc, String assistantText) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("parseRankingFromAssistant", String.class);
        m.setAccessible(true);
        return (List<?>) m.invoke(svc, assistantText);
    }

    private static String invokeBuildRerankPrompt(String query, List<Map<String, Object>> docs) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("buildRerankPrompt", String.class, List.class);
        m.setAccessible(true);
        return (String) m.invoke(null, query, docs);
    }
}

