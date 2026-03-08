package com.example.EnterpriseRagCommunity.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AiRerankServiceBranchCoverageTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static Object invokePrivate(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static JsonNode node(String json) throws Exception {
        return OM.readTree(json);
    }

    @Test
    void parseResults_locatesResultsContainer_outputResults_results_data_rankings_ranks_rootArray() {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertEquals(1, svc.parseResults("""
                {"output":{"results":[{"index":1,"relevance_score":0.9}]}}
                """).size());

        assertEquals(1, svc.parseResults("""
                {"results":[{"index":2,"relevance_score":0.8}]}
                """).size());

        assertEquals(1, svc.parseResults("""
                {"data":[{"index":3,"relevance_score":0.7}]}
                """).size());

        assertEquals(1, svc.parseResults("""
                {"rankings":[{"index":4,"relevance_score":0.6}]}
                """).size());

        assertEquals(1, svc.parseResults("""
                {"ranks":[{"index":5,"relevance_score":0.5}]}
                """).size());

        assertEquals(1, svc.parseResults("""
                [{"index":6,"relevance_score":0.4}]
                """).size());

        assertEquals(0, svc.parseResults("""
                {"output":{"results":{"index":1,"relevance_score":0.9}}}
                """).size());
    }

    @Test
    void parseResults_indexAliases_and_posFallback_and_nullElementsSkipped() {
        AiRerankService svc = new AiRerankService(null, null, null);

        String raw = """
                {
                  "results": [
                    null,
                    {"index": 10, "relevance_score": 0.1},
                    {"doc_index": 11, "relevance_score": 0.1},
                    {"document_index": 12, "relevance_score": 0.1},
                    {"documentIndex": 13, "relevance_score": 0.1},
                    {"idx": 14, "relevance_score": 0.1},
                    {"i": 15, "relevance_score": 0.1},
                    {"position": 16, "relevance_score": 0.1},
                    {"id": 17, "relevance_score": 0.1},
                    {"relevance_score": 0.1}
                  ]
                }
                """;

        List<AiRerankService.RerankHit> hits = svc.parseResults(raw);
        assertEquals(9, hits.size());

        assertEquals(10, hits.get(0).index());
        assertEquals(11, hits.get(1).index());
        assertEquals(12, hits.get(2).index());
        assertEquals(13, hits.get(3).index());
        assertEquals(14, hits.get(4).index());
        assertEquals(15, hits.get(5).index());
        assertEquals(16, hits.get(6).index());
        assertEquals(17, hits.get(7).index());

        assertEquals(8, hits.get(8).index());
    }

    @Test
    void parseResults_scoreAliases_default0_and_percentNormalize_onlyFor_1_100() {
        AiRerankService svc = new AiRerankService(null, null, null);

        List<AiRerankService.RerankHit> hits = svc.parseResults("""
                {
                  "results": [
                    {"index":0,"relevance_score": 0.9},
                    {"index":1,"score": 0.8},
                    {"index":2,"relevanceScore": 0.7},
                    {"index":3,"relevance": 0.6},
                    {"index":4,"confidence": 0.5},
                    {"index":5},
                    {"index":6,"score": 50},
                    {"index":7,"score": 1},
                    {"index":8,"score": 101}
                  ]
                }
                """);

        assertEquals(9, hits.size());
        assertEquals(0.9, hits.get(0).relevanceScore(), 1e-12);
        assertEquals(0.8, hits.get(1).relevanceScore(), 1e-12);
        assertEquals(0.7, hits.get(2).relevanceScore(), 1e-12);
        assertEquals(0.6, hits.get(3).relevanceScore(), 1e-12);
        assertEquals(0.5, hits.get(4).relevanceScore(), 1e-12);

        assertEquals(0.0, hits.get(5).relevanceScore(), 1e-12);
        assertEquals(0.5, hits.get(6).relevanceScore(), 1e-12);
        assertEquals(1.0, hits.get(7).relevanceScore(), 1e-12);
        assertEquals(101.0, hits.get(8).relevanceScore(), 1e-12);
    }

    @Test
    void parseResults_invalidOrEmptyJson_returnsEmptyList() {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertEquals(0, svc.parseResults(null).size());
        assertEquals(0, svc.parseResults("   ").size());
        assertEquals(0, svc.parseResults("{not-json").size());
    }

    @Test
    void normalizeRerankJsonFromResponsesResponse_object_array_codeFence_empty_noJson() throws Exception {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertEquals("{\"results\":[{\"index\":0}]}",
                svc.normalizeRerankJsonFromResponsesResponse("""
                        {"output_text":"{\\"results\\":[{\\"index\\":0}]}"}
                        """));

        assertEquals("{\"results\":[{\"index\":1}]}",
                svc.normalizeRerankJsonFromResponsesResponse("""
                        {"output_text":"[{\\"index\\":1}]"}
                        """));

        assertEquals("{\"results\":[{\"index\":2}]}",
                svc.normalizeRerankJsonFromResponsesResponse("""
                        {"output_text":"```json\\n{\\"results\\":[{\\"index\\":2}]}\\n```"}
                        """));

        assertThrows(Exception.class, () -> svc.normalizeRerankJsonFromResponsesResponse("""
                {"output_text":"   "}
                """));

        assertThrows(Exception.class, () -> svc.normalizeRerankJsonFromResponsesResponse("""
                {"output_text":"hello world"}
                """));
    }

    @Test
    void normalizeRerankJsonFromChatResponse_private_object_array_codeFence_empty_noJson() throws Exception {
        AiRerankService svc = new AiRerankService(null, null, null);

        String chatObj = """
                {"choices":[{"message":{"content":"{\\"results\\":[{\\"index\\":0}]}"}}]}
                """;
        assertEquals("{\"results\":[{\"index\":0}]}",
                invokePrivate(svc, "normalizeRerankJsonFromChatResponse", new Class<?>[]{String.class}, chatObj));

        String chatArr = """
                {"output":{"text":"[{\\"index\\":1}]"}}
                """;
        assertEquals("{\"results\":[{\"index\":1}]}",
                invokePrivate(svc, "normalizeRerankJsonFromChatResponse", new Class<?>[]{String.class}, chatArr));

        String chatFence = """
                {"message":{"content":"```\\n{\\"results\\":[{\\"index\\":2}]}\\n```"}}
                """;
        assertEquals("{\"results\":[{\"index\":2}]}",
                invokePrivate(svc, "normalizeRerankJsonFromChatResponse", new Class<?>[]{String.class}, chatFence));

        String chatEmpty = """
                {"choices":[{"message":{"content":"   "}}]}
                """;
        assertThrows(Exception.class, () -> invokePrivate(svc, "normalizeRerankJsonFromChatResponse", new Class<?>[]{String.class}, chatEmpty));

        String chatNoJson = """
                {"choices":[{"message":{"content":"no json here"}}]}
                """;
        assertThrows(Exception.class, () -> invokePrivate(svc, "normalizeRerankJsonFromChatResponse", new Class<?>[]{String.class}, chatNoJson));
    }

    @Test
    void extractAssistantContent_private_covers_shapes_and_fallback() throws Exception {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertEquals("A", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"choices":[{"message":{"content":"A"}}]}
                """));

        assertEquals("B", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"choices":[{"text":"B"}]}
                """));

        assertEquals("C", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"output":"C"}
                """));

        assertEquals("D1", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"output":{"text":"D1"}}
                """));
        assertEquals("D2", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"output":{"content":"D2"}}
                """));
        assertEquals("D3", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"output":{"message":"D3"}}
                """));
        assertEquals("D4", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"output":{"message":{"content":"D4"}}}
                """));

        assertEquals("E1", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"response":"E1"}
                """));
        assertEquals("E2", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"result":"E2"}
                """));
        assertEquals("E3", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"message":"E3"}
                """));
        assertEquals("E4", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, """
                {"message":{"content":"E4"}}
                """));

        assertEquals("{bad", invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, " {bad "));
    }

    @Test
    void extractResponsesOutputText_private_covers_shapes_and_fallback() throws Exception {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertEquals("A", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, """
                {"output_text":"A"}
                """));

        assertEquals("B", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, """
                {"output":[{"content":[{"text":"B"}]}]}
                """));

        assertEquals("C", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, """
                {"output":[{"content":["C"]}]}
                """));

        assertEquals("D", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, """
                {"output":[{"text":"D"}]}
                """));

        assertEquals("E1", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, """
                {"response":"E1"}
                """));
        assertEquals("E2", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, """
                {"result":"E2"}
                """));

        assertEquals("{bad", invokePrivate(svc, "extractResponsesOutputText", new Class<?>[]{String.class}, " {bad "));
    }

    @Test
    void parseUsageFromJson_usageLocation_and_alias_and_stringNumber_and_unparseableNull() {
        AiRerankService svc = new AiRerankService(null, null, null);

        assertEquals(12, svc.parseUsageFromJson("""
                {"usage":{"total_tokens":12}}
                """).totalTokens());

        assertEquals(34, svc.parseUsageFromJson("""
                {"output":{"usage":{"totalTokens":"34"}}}
                """).totalTokens());

        assertNull(svc.parseUsageFromJson("""
                {"usage":123}
                """));

        assertNull(svc.parseUsageFromJson("""
                {"usage":{"total_tokens":"not-a-number"}}
                """));
    }

    @Test
    void readIntLike_private_numeric_string_empty_nan_infinity_illegal() throws Exception {
        assertEquals(2, invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("2")));
        assertEquals(3, invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("2.6")));

        assertEquals(7, invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"7\"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"  \"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"NaN\"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"Infinity\"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readIntLike", new Class<?>[]{JsonNode.class}, node("\"oops\"")));
    }

    @Test
    void readDoubleLike_private_numeric_string_empty_nan_infinity_illegal() throws Exception {
        assertEquals(1.25, (Double) invokePrivateStatic(AiRerankService.class, "readDoubleLike", new Class<?>[]{JsonNode.class}, node("1.25")), 1e-12);
        assertEquals(2.5, (Double) invokePrivateStatic(AiRerankService.class, "readDoubleLike", new Class<?>[]{JsonNode.class}, node("\"2.5\"")), 1e-12);

        assertNull(invokePrivateStatic(AiRerankService.class, "readDoubleLike", new Class<?>[]{JsonNode.class}, node("\"\"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readDoubleLike", new Class<?>[]{JsonNode.class}, node("\"NaN\"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readDoubleLike", new Class<?>[]{JsonNode.class}, node("\"Infinity\"")));
        assertNull(invokePrivateStatic(AiRerankService.class, "readDoubleLike", new Class<?>[]{JsonNode.class}, node("\"oops\"")));
    }

    @Test
    void normalizeOpenAiCompatBaseUrl_and_resolveResponsesBaseUrl_private_branches() throws Exception {
        assertEquals("", invokePrivateStatic(AiRerankService.class, "normalizeOpenAiCompatBaseUrl", new Class<?>[]{String.class}, (String) null));
        assertEquals("", invokePrivateStatic(AiRerankService.class, "normalizeOpenAiCompatBaseUrl", new Class<?>[]{String.class}, "   "));

        assertEquals("https://x.com/v1", invokePrivateStatic(AiRerankService.class, "normalizeOpenAiCompatBaseUrl", new Class<?>[]{String.class}, "https://x.com/"));
        assertEquals("https://x.com/v1", invokePrivateStatic(AiRerankService.class, "normalizeOpenAiCompatBaseUrl", new Class<?>[]{String.class}, "https://x.com/v1"));
        assertEquals("https://x.com/v1/chat/completions", invokePrivateStatic(AiRerankService.class, "normalizeOpenAiCompatBaseUrl", new Class<?>[]{String.class}, "https://x.com/v1/chat/completions"));
        assertEquals("https://x.com/api/v1", invokePrivateStatic(AiRerankService.class, "normalizeOpenAiCompatBaseUrl", new Class<?>[]{String.class}, "https://x.com/api/v1"));

        assertEquals("https://api.example.com/v1", invokePrivateStatic(AiRerankService.class, "resolveResponsesBaseUrl", new Class<?>[]{String.class, String.class}, "https://base.invalid", "https://api.example.com/v1/responses"));
        assertEquals("https://api.example.com/v1", invokePrivateStatic(AiRerankService.class, "resolveResponsesBaseUrl", new Class<?>[]{String.class, String.class}, "https://base.invalid", "https://api.example.com/v1/responses/"));
        assertEquals("https://api.example.com/v123", invokePrivateStatic(AiRerankService.class, "resolveResponsesBaseUrl", new Class<?>[]{String.class, String.class}, "https://base.invalid", "https://api.example.com/v123/"));
        assertEquals("https://api.example.com/v2/rerank", invokePrivateStatic(AiRerankService.class, "resolveResponsesBaseUrl", new Class<?>[]{String.class, String.class}, "https://base.invalid", "https://api.example.com/v2/rerank"));
        assertEquals("https://x.com/v1", invokePrivateStatic(AiRerankService.class, "resolveResponsesBaseUrl", new Class<?>[]{String.class, String.class}, "https://x.com", "/v1/rerank"));
    }

    @Test
    void privateHelpers_should_cover_dashscope_status_extract_prompt_escape_strip_shrink() throws Exception {
        assertEquals(true, invokePrivateStatic(AiRerankService.class, "isDashscopeProvider", new Class<?>[]{String.class, String.class}, "aliyun", null));
        assertEquals(true, invokePrivateStatic(AiRerankService.class, "isDashscopeProvider", new Class<?>[]{String.class, String.class}, "p1", "https://dashscope.aliyuncs.com/compatible-mode/v1"));
        assertEquals(false, invokePrivateStatic(AiRerankService.class, "isDashscopeProvider", new Class<?>[]{String.class, String.class}, "p1", "https://api.example.com"));

        assertEquals(true, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{java.io.IOException.class, int[].class}, new java.io.IOException("HTTP 404 not found"), new int[]{400, 404}));
        assertEquals(false, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{java.io.IOException.class, int[].class}, new java.io.IOException("HTTP 500"), new int[]{400, 404}));
        assertEquals(false, invokePrivateStatic(AiRerankService.class, "isHttpStatus", new Class<?>[]{java.io.IOException.class, int[].class}, null, new int[]{400}));

        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, null, "k"));
        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of(), "k"));
        assertNull(invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of("k", "   "), "k"));
        assertEquals("v", invokePrivateStatic(AiRerankService.class, "extractString", new Class<?>[]{Map.class, String.class}, Map.of("k", " v "), "k"));

        String prompt = (String) invokePrivateStatic(AiRerankService.class, "buildRerankUserPrompt", new Class<?>[]{String.class, List.class, Integer.class}, "q", Arrays.asList("d1", null), 0);
        assertTrue(prompt.contains("\"query\": \"q\""));
        assertFalse(prompt.contains("\"topN\":"));
        assertTrue(prompt.contains("\"d1\""));
        assertTrue(prompt.contains("\"\""));

        String escaped = (String) invokePrivateStatic(AiRerankService.class, "objectMapperSafeString", new Class<?>[]{String.class}, "\"\\\n\t");
        assertEquals("\"\\\"\\\\\\n\\t\"", escaped);
        assertEquals("\"\"", invokePrivateStatic(AiRerankService.class, "objectMapperSafeString", new Class<?>[]{String.class}, (String) null));

        assertEquals("{\"a\":1}", invokePrivateStatic(AiRerankService.class, "stripCodeFences", new Class<?>[]{String.class}, "```json\n{\"a\":1}\n```"));
        assertEquals("x", invokePrivateStatic(AiRerankService.class, "stripCodeFences", new Class<?>[]{String.class}, " x "));
        assertEquals("", invokePrivateStatic(AiRerankService.class, "shrink", new Class<?>[]{String.class, int.class}, null, 10));
        assertEquals("abc", invokePrivateStatic(AiRerankService.class, "shrink", new Class<?>[]{String.class, int.class}, " abc ", 10));
        assertEquals("ab...", invokePrivateStatic(AiRerankService.class, "shrink", new Class<?>[]{String.class, int.class}, "abcdef", 5));
    }

    @Test
    void normalizeModel_override_metadata_default_finalFallback() {
        assertEquals("m1", AiRerankService.normalizeModel(" m1 ", Map.of("defaultRerankModel", "m2")));
        assertEquals("m2", AiRerankService.normalizeModel("  ", Map.of("defaultRerankModel", " m2 ")));
        assertEquals("qwen3-rerank", AiRerankService.normalizeModel(null, Map.of()));
        assertEquals("qwen3-rerank", AiRerankService.normalizeModel(" ", null));
    }
}
