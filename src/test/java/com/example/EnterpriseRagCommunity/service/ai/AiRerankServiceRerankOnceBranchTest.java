package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService.ResolvedProvider;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService.TaskHandle;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.config.RetrievalRagProperties;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AiRerankServiceRerankOnceBranchTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @BeforeEach
    void setupMockHttp() {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
    }

    private static AiRerankService newService(
            AiProvidersConfigService aiProvidersConfigService,
            LlmCallQueueService llmCallQueueService,
            PromptsRepository promptsRepository
    ) {
        return new AiRerankService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
    }

    private static void stubQueueExecutesSupplier(LlmCallQueueService llmCallQueueService, TaskHandle task) throws Exception {
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> sup =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) inv.getArgument(4);
            return sup.get(task);
        });
    }

    private static ResolvedProvider provider(
            String id,
            String type,
            String baseUrl,
            Map<String, Object> metadata
    ) {
        return new ResolvedProvider(
                id,
                type,
                baseUrl,
                "k",
                "m-chat",
                "m-embed",
                metadata == null ? Map.of() : metadata,
                Map.of("X-Test", "1"),
                1000,
                1000
        );
    }

    private static PromptsEntity promptEntity(String sys) {
        PromptsEntity e = new PromptsEntity();
        e.setSystemPrompt(sys);
        return e;
    }

    private static JsonNode parseBody(MockHttpUrl.RequestCapture rc) throws Exception {
        return OM.readTree(new String(rc.body(), StandardCharsets.UTF_8));
    }

    private static List<MockHttpUrl.RequestCapture> drainDistinctRequests() {
        List<MockHttpUrl.RequestCapture> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (true) {
            MockHttpUrl.RequestCapture rc = MockHttpUrl.pollRequest();
            if (rc == null) break;
            String key = rc.method() + "|" + rc.url() + "|" + new String(rc.body(), StandardCharsets.UTF_8);
            if (seen.add(key)) out.add(rc);
        }
        return out;
    }

    @Test
    void hybridRag_shouldCoverCoreBranches_viaPublicAndReflection() throws Exception {
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":1.0,"_source":{"post_id":10,"chunk_index":0,"board_id":1,"title":"t1","content_text":"c1"}},
                  {"_id":"d2","_score":0.9,"_source":{"post_id":11,"chunk_index":1,"board_id":1,"title":"t2","content_text":"c2"}},
                  {"_id":"d3","_score":0.8,"_source":{"post_id":12,"chunk_index":2,"board_id":1,"title":"t3","content_text":"c3"}}
                ]}}
                """);
        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.7,"_source":{"post_id":10,"chunk_index":0,"board_id":1,"title":"t1","content_text":"c1"}},
                  {"_id":"d2","_score":0.6,"_source":{"post_id":11,"chunk_index":1,"board_id":1,"title":"t2","content_text":"c2"}}
                ]}}
                """);

        RetrievalRagProperties ragProps = new RetrievalRagProperties();
        ragProps.getEs().setIndex("idx_posts");
        ragProps.getEs().setEmbeddingModel("em1");
        ragProps.getEs().setEmbeddingDims(3);

        RagPostsIndexService indexService = mock(RagPostsIndexService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris")).thenReturn("mockhttp://es/");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY")).thenReturn("k1");
        when(llmGateway.embedOnceRouted(eq(LlmQueueTaskType.POST_EMBEDDING), eq(null), eq("em1"), eq("q")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, 3, "em1"));
        when(llmGateway.rerankOnceRouted(any(), any(), any(), any(), anyList(), any(), any(), any(), any()))
                .thenThrow(new IOException("upstream down"));

        PostsEntity p10 = new PostsEntity();
        p10.setId(10L);
        PostsEntity p11 = new PostsEntity();
        p11.setId(11L);
        PostsEntity p12 = new PostsEntity();
        p12.setId(12L);
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p10, p11, p12));

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                ragProps,
                indexService,
                mock(AiEmbeddingService.class),
                new ObjectMapper(),
                mock(AiRerankService.class),
                llmGateway,
                postsRepository,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        HybridRagRetrievalService.RetrieveResult early = svc.retrieve(null, 1L, null, true);
        assertEquals("", early.getQueryText());
        assertTrue(early.getFinalHits().isEmpty());

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(3);
        cfg.setVecK(2);
        cfg.setHybridK(0);
        cfg.setMaxDocs(10);
        cfg.setRerankEnabled(true);
        cfg.setRerankK(2);
        cfg.setFusionMode("bad");
        HybridRagRetrievalService.RetrieveResult out = svc.retrieve("q", 1L, cfg, true);
        assertEquals(3, out.getBm25Hits().size());
        assertEquals(2, out.getVecHits().size());
        assertEquals(1, out.getFinalHits().size());
        assertNotNull(out.getRerankError());

        List<HybridRagRetrievalService.DocHit> fused = (List<HybridRagRetrievalService.DocHit>) invokePrivate(
                svc,
                "fuse",
                new Class<?>[]{List.class, List.class, List.class, HybridRetrievalConfigDTO.class, int.class},
                List.of(doc("A", 10.0), doc("B", 9.0), doc("C", 8.0)),
                List.of(doc("B", 0.9), doc("D", 0.8), doc("A", 0.7)),
                null,
                cfg,
                2
        );
        assertEquals(2, fused.size());
        assertEquals("B", fused.get(0).getDocId());
        assertEquals("A", fused.get(1).getDocId());

        MockHttpUrl.enqueue(200, """
                {"hits":{"hits":[{"_id":"x","_score":0.5,"_source":{"post_id":10,"chunk_index":0,"board_id":1,"title":"t1","content_text":"c1"}}]}}
                """);
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es-one:9200/ , mockhttp://es-two:9200");
        JsonNode ok = (JsonNode) invokePrivate(svc, "postSearch", new Class<?>[]{String.class, String.class}, "idx_posts", "{\"size\":1}");
        List<HybridRagRetrievalService.DocHit> hits = (List<HybridRagRetrievalService.DocHit>) invokePrivate(svc, "parseEsHits", new Class<?>[]{JsonNode.class}, ok);
        assertTrue(hits.size() >= 1);

        String content = (String) invokePrivate(svc, "extractAssistantContent", new Class<?>[]{String.class}, "{\"choices\":[{\"message\":{\"content\":\"HELLO\"}}]}");
        assertEquals("HELLO", content);
        assertEquals(1, ((List<?>) invokePrivate(svc, "parseRankingFromAssistant", new Class<?>[]{String.class}, "{\"ranked\":[{\"doc_id\":\"d1\"}]}")).size());
        assertEquals("1.23", invokePrivateStatic(HybridRagRetrievalService.class, "trimTrailingZeros", new Class<?>[]{double.class}, 1.2300));
    }

    private static HybridRagRetrievalService.DocHit doc(String id, Double score) {
        HybridRagRetrievalService.DocHit h = new HybridRagRetrievalService.DocHit();
        h.setDocId(id);
        h.setScore(score);
        h.setTitle(id);
        h.setContentText("text " + id);
        return h;
    }

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

    @Test
    void rerankOnce_should_use_dashscope_branch_and_sort_results() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "aliyun",
                "OPENAI_COMPAT",
                "mockhttp://dashscope.aliyuncs.com/compatible-mode/v1",
                Map.of()
        );
        when(aiProvidersConfigService.resolveProvider("aliyun")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        doThrow(new RuntimeException("ignore")).when(task).reportInput(anyString());
        doThrow(new RuntimeException("ignore")).when(task).reportOutput(anyString());
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(200, """
                {"results":[{"index":1,"relevance_score":0.1},{"index":0,"relevance_score":0.9}],"usage":{"total_tokens":10}}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "aliyun",
                null,
                "q",
                List.of("d1", "d2"),
                2,
                null,
                null,
                null
        );

        assertEquals("aliyun", r.providerId());
        assertEquals("qwen3-rerank", r.model());
        assertEquals(10, r.totalTokens());
        assertEquals(List.of(0, 1), r.results().stream().map(AiRerankService.RerankHit::index).toList());

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertTrue(req.url().toString().contains("/compatible-api/v1/reranks"));
    }

    @Test
    void rerankOnce_should_use_responses_style_rerank_endpoint_when_configured() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://rerank.example.com",
                Map.of("rerankEndpointPath", "/v1/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(200, """
                {"output_text":"{\\"results\\":[{\\"index\\":0,\\"relevance_score\\":0.2},{\\"index\\":1,\\"relevance_score\\":0.9}]}","usage":{"total_tokens":7}}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1", "d2"),
                2,
                null,
                null,
                null
        );

        assertEquals("p1", r.providerId());
        assertEquals("m1", r.model());
        assertEquals(7, r.totalTokens());
        assertEquals(List.of(1, 0), r.results().stream().map(AiRerankService.RerankHit::index).toList());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(1, reqs.size());
        MockHttpUrl.RequestCapture req = reqs.get(0);
        assertTrue(req.url().toString().contains("/v1/rerank"));

        JsonNode body = parseBody(req);
        assertTrue(body.has("input"));
        assertEquals("m1", body.get("model").asText());
    }

    @Test
    void rerankOnce_should_resolve_active_provider_when_provider_id_blank() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider active = provider(
                "active-p1",
                "OPENAI_COMPAT",
                "mockhttp://rerank.example.com",
                Map.of("rerankEndpointPath", "/v1/rerank")
        );
        when(aiProvidersConfigService.resolveActiveProvider()).thenReturn(active);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(200, """
                {"output_text":"{\\"results\\":[{\\"index\\":0,\\"relevance_score\\":0.6}]}","usage":{"total_tokens":5}}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "   ",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals("active-p1", r.providerId());
        assertEquals(5, r.totalTokens());
        verify(aiProvidersConfigService, times(1)).resolveActiveProvider();
        verify(aiProvidersConfigService, never()).resolveProvider(anyString());
    }

    @Test
    void rerankOnce_should_handle_blank_provider_and_null_active_provider_when_queue_returns_null() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        when(aiProvidersConfigService.resolveActiveProvider()).thenReturn(null);
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenReturn(null);

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "   ",
                null,
                null,
                null,
                0,
                "   ",
                null,
                null
        );

        assertEquals("", r.providerId());
        assertEquals("qwen3-rerank", r.model());
        assertNull(r.totalTokens());
        assertTrue(r.results().isEmpty());
    }

    @Test
    void rerankOnce_should_fallback_to_default_compat_endpoint_and_normalize_topn_to_one() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "   ")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);
        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 6, 2));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(415, "{\"error\":\"unsupported\"}");
        MockHttpUrl.enqueue(200, """
                {"results":[{"index":0,"relevance_score":0.9}]}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                0,
                "   ",
                null,
                null
        );

        assertEquals(6, r.totalTokens());
        assertEquals(List.of(0), r.results().stream().map(AiRerankService.RerankHit::index).toList());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(2, reqs.size());
        assertTrue(reqs.get(0).url().toString().contains("/compatible-api/v1/reranks"));
        JsonNode localBody = parseBody(reqs.get(1));
        assertEquals(1, localBody.path("top_n").asInt());
    }

    @Test
    void rerankOnce_should_fallback_to_local_when_responses_style_returns_http_404() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://rerank.example.com",
                Map.of("rerankEndpointPath", "/v1/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 9, 2));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(404, "{\"error\":\"no rerank\"}");
        MockHttpUrl.enqueue(200, """
                {"results":[{"index":0,"relevance_score":0.9},{"index":1,"relevance_score":0.1}]}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1", "d2"),
                2,
                null,
                null,
                null
        );

        assertEquals(9, r.totalTokens());
        assertEquals(List.of(0, 1), r.results().stream().map(AiRerankService.RerankHit::index).toList());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(2, reqs.size());

        JsonNode body1 = parseBody(reqs.get(0));
        JsonNode body2 = parseBody(reqs.get(1));
        boolean ok = (body1.has("input") && body2.has("query")) || (body2.has("input") && body1.has("query"));
        assertTrue(ok);
    }

    @Test
    void rerankOnce_should_throw_directly_when_responses_style_returns_http_500() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://rerank.example.com",
                Map.of("rerankEndpointPath", "/v1/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(500, "{\"error\":\"responses failed\"}");

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        IOException ex = assertThrows(IOException.class, () -> svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        ));
        assertNotNull(ex.getMessage());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(1, reqs.size());
        assertTrue(reqs.get(0).url().toString().contains("/v1/rerank"));
    }

    @Test
    void rerankOnce_should_use_dashscope_compat_endpoint_when_configured() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://dashscope-compat.example.com",
                Map.of("rerankEndpointPath", "/compatible-api/v1/reranks")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(200, """
                {"results":[{"index":0,"relevance_score":0.9}],"usage":{"total_tokens":3}}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals(3, r.totalTokens());
        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(1, reqs.size());
        assertTrue(reqs.get(0).url().toString().contains("/compatible-api/v1/reranks"));
    }

    @Test
    void rerankOnce_should_throw_directly_when_dashscope_compat_returns_http_500() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://dashscope-compat.example.com",
                Map.of("rerankEndpointPath", "/compatible-api/v1/reranks")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(500, "{\"error\":\"compat failed\"}");

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        IOException ex = assertThrows(IOException.class, () -> svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        ));
        assertTrue(ex.getMessage().contains("HTTP 500"));

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(1, reqs.size());
        assertTrue(reqs.get(0).url().toString().contains("/compatible-api/v1/reranks"));
    }

    @Test
    void rerankOnce_should_fallback_to_local_when_dashscope_compat_returns_http_415() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://dashscope-compat.example.com",
                Map.of("rerankEndpointPath", "/compatible-api/v1/reranks")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 6, 2));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(415, "{\"error\":\"unsupported\"}");
        MockHttpUrl.enqueue(200, """
                {"results":[{"index":0,"relevance_score":0.9}]}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals(6, r.totalTokens());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(2, reqs.size());

        boolean hasCompat = false;
        boolean hasLocal = false;
        for (MockHttpUrl.RequestCapture rc : reqs) {
            if (rc.url().toString().contains("/compatible-api/v1/reranks")) hasCompat = true;
            if (parseBody(rc).has("query")) hasLocal = true;
        }
        assertTrue(hasCompat);
        assertTrue(hasLocal);
    }

    @Test
    void rerankOnce_should_use_openai_responses_prompt_when_endpoint_contains_responses() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://openai.example.com",
                Map.of("rerankEndpointPath", "/responses")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 8, 2));

        when(promptsRepository.findByPromptCode("RERANK_DEFAULT"))
                .thenReturn(Optional.of(promptEntity("SYS")));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(200, """
                {"output_text":"{\\"results\\":[{\\"index\\":0,\\"relevance_score\\":0.9}]}"}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                "i",
                null,
                null
        );

        assertEquals(8, r.totalTokens());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(1, reqs.size());
        MockHttpUrl.RequestCapture req = reqs.get(0);
        assertTrue(req.url().toString().endsWith("/responses"));

        JsonNode body = parseBody(req);
        assertEquals("m1", body.get("model").asText());
    }

    @Test
    void rerankOnce_local_openai_compat_should_fallback_responses_then_chat_on_http_404() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "LOCAL_OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 11, 2));

        when(promptsRepository.findByPromptCode("RERANK_DEFAULT"))
                .thenReturn(Optional.of(promptEntity("SYS")));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(404, "{\"error\":\"no rerank\"}");
        MockHttpUrl.enqueue(404, "{\"error\":\"no responses\"}");
        MockHttpUrl.enqueue(200, """
                {"choices":[{"message":{"content":"{\\"results\\":[{\\"index\\\":0,\\"relevance_score\\\":0.9}]}"}}]}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals(11, r.totalTokens());
        assertEquals(List.of(0), r.results().stream().map(AiRerankService.RerankHit::index).toList());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(3, reqs.size());
        boolean hasRerank = false;
        boolean hasResponses = false;
        boolean hasChat = false;
        for (MockHttpUrl.RequestCapture rc : reqs) {
            String u = rc.url().toString();
            if (u.contains("/rerank")) hasRerank = true;
            if (u.endsWith("/responses")) hasResponses = true;
            if (u.contains("/chat/completions")) hasChat = true;
        }
        assertTrue(hasRerank);
        assertTrue(hasResponses);
        assertTrue(hasChat);
    }

    @Test
    void rerankOnce_local_openai_compat_should_fallback_to_responses_success_without_chat() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "LOCAL_OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 13, 2));
        when(promptsRepository.findByPromptCode("RERANK_DEFAULT"))
                .thenReturn(Optional.of(promptEntity("SYS")));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(404, "{\"error\":\"no rerank\"}");
        MockHttpUrl.enqueue(200, """
                {"output_text":"{\\"results\\":[{\\"index\\":0,\\"relevance_score\\":0.9}]}"}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals(13, r.totalTokens());
        assertEquals(List.of(0), r.results().stream().map(AiRerankService.RerankHit::index).toList());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(2, reqs.size());
        boolean hasRerank = false;
        boolean hasResponses = false;
        boolean hasChat = false;
        for (MockHttpUrl.RequestCapture rc : reqs) {
            String u = rc.url().toString();
            if (u.contains("/rerank")) hasRerank = true;
            if (u.endsWith("/responses")) hasResponses = true;
            if (u.contains("/chat/completions")) hasChat = true;
        }
        assertTrue(hasRerank);
        assertTrue(hasResponses);
        assertFalse(hasChat);
    }

    @Test
    void rerankOnce_local_openai_compat_should_throw_when_http_500_not_maybe_unsupported() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "LOCAL_OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(500, "{\"error\":\"boom\"}");

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        IOException ex = assertThrows(IOException.class, () -> svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        ));
        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

    @Test
    void rerankOnce_non_local_openai_compat_should_use_chat_prompt_when_local_returns_http_404() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(llmCallQueueService.parseOpenAiUsageFromJson(anyString()))
                .thenReturn(new LlmCallQueueService.UsageMetrics(1, 2, 12, 2));

        when(promptsRepository.findByPromptCode("RERANK_DEFAULT"))
                .thenReturn(Optional.of(promptEntity("SYS")));

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(404, "{\"error\":\"no rerank\"}");
        MockHttpUrl.enqueue(200, """
                {"choices":[{"message":{"content":"{\\"results\\":[{\\"index\\":0,\\"relevance_score\\":0.9}]}"}}]}
                """.trim());

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals(12, r.totalTokens());

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(2, reqs.size());
        boolean hasRerank = false;
        boolean hasChat = false;
        for (MockHttpUrl.RequestCapture rc : reqs) {
            String u = rc.url().toString();
            if (u.contains("/rerank")) hasRerank = true;
            if (u.contains("/chat/completions")) hasChat = true;
        }
        assertTrue(hasRerank);
        assertTrue(hasChat);
    }

    @Test
    void rerankOnce_non_local_openai_compat_should_throw_when_local_returns_http_500() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(500, "{\"error\":\"boom\"}");

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        IOException ex = assertThrows(IOException.class, () -> svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        ));

        List<MockHttpUrl.RequestCapture> reqs = drainDistinctRequests();
        assertEquals(1, reqs.size());
        assertTrue(reqs.get(0).url().toString().contains("/rerank"));
    }

    @Test
    void rerankOnce_should_return_empty_when_queue_result_is_null() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);
        when(llmCallQueueService.call(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                anyInt(),
                any(LlmCallQueueService.CheckedTaskSupplier.class),
                any(LlmCallQueueService.ResultMetricsExtractor.class)
        )).thenReturn(null);

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        AiRerankService.RerankResult r = svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        );

        assertEquals("p1", r.providerId());
        assertEquals("m1", r.model());
        assertNull(r.totalTokens());
        assertNotNull(r.results());
        assertTrue(r.results().isEmpty());
        assertNull(MockHttpUrl.pollRequest());
    }

    @Test
    void rerankOnce_should_wrap_non_io_exception_as_ioexception() throws Exception {
        AiProvidersConfigService aiProvidersConfigService = mock(AiProvidersConfigService.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ResolvedProvider p = provider(
                "p1",
                "OPENAI_COMPAT",
                "mockhttp://openai-compat.example.com",
                Map.of("rerankEndpointPath", "/rerank")
        );
        when(aiProvidersConfigService.resolveProvider("p1")).thenReturn(p);

        when(promptsRepository.findByPromptCode("RERANK_DEFAULT"))
                .thenReturn(Optional.empty());

        TaskHandle task = mock(TaskHandle.class);
        when(task.id()).thenReturn("t1");
        stubQueueExecutesSupplier(llmCallQueueService, task);

        MockHttpUrl.enqueue(404, "{\"error\":\"no rerank\"}");

        AiRerankService svc = newService(aiProvidersConfigService, llmCallQueueService, promptsRepository);
        IOException ex = assertThrows(IOException.class, () -> svc.rerankOnce(
                "p1",
                "m1",
                "q",
                List.of("d1"),
                1,
                null,
                null,
                null
        ));
        assertTrue(ex.getMessage().contains("Rerank failed:"));
    }
}
