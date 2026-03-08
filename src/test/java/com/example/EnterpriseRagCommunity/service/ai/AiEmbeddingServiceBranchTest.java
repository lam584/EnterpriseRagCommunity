package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService.ResolvedProvider;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AiEmbeddingServiceBranchTest {

    @BeforeEach
    void setupMockHttp() {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
    }

    private static ResolvedProvider provider(
            String id,
            String baseUrl,
            String apiKey,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
        return new ResolvedProvider(
                id,
                "OPENAI_COMPAT",
                baseUrl,
                apiKey,
                "chat",
                "embed",
                Map.of(),
                extraHeaders == null ? Map.of() : extraHeaders,
                connectTimeoutMs,
                readTimeoutMs
        );
    }

    private static void stubCallDedupReturnsNull(LlmCallQueueService queue) throws Exception {
        when(queue.callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any()))
                .thenReturn(null);
    }

    private static void stubCallDedupExecutesSupplier(
            LlmCallQueueService queue,
            LlmCallQueueService.TaskHandle task,
            AtomicReference<LlmCallQueueService.UsageMetrics> metricsFromNull,
            AtomicReference<LlmCallQueueService.UsageMetrics> metricsFromResult
    ) throws Exception {
        when(queue.callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    LlmCallQueueService.CheckedTaskSupplier<Object> supplier = (LlmCallQueueService.CheckedTaskSupplier<Object>) inv.getArgument(5);
                    @SuppressWarnings("unchecked")
                    LlmCallQueueService.ResultMetricsExtractor<Object> mex = (LlmCallQueueService.ResultMetricsExtractor<Object>) inv.getArgument(6);
                    Object r = supplier.get(task);
                    if (mex != null) {
                        metricsFromNull.set(mex.extract(null));
                        metricsFromResult.set(mex.extract(r));
                    }
                    return r;
                });
    }

    @Test
    void embedOnce_shouldDelegate_to_embedOnceForTask_withEmbeddingTaskType_andActiveProvider() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupReturnsNull(queue);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "http://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        assertNull(svc.embedOnce("x", "m1"));

        verify(providers).resolveActiveProvider();
        verify(providers, never()).resolveProvider(anyString());

        ArgumentCaptor<LlmQueueTaskType> type = ArgumentCaptor.forClass(LlmQueueTaskType.class);
        verify(queue).callDedup(type.capture(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());
        assertEquals(LlmQueueTaskType.EMBEDDING, type.getValue());
    }

    @Test
    void embedOnce_withProviderId_shouldDelegate_withEmbeddingTaskType_andResolveProviderTrimmed() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupReturnsNull(queue);

        when(providers.resolveProvider("p1")).thenReturn(provider("p1", "http://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        assertNull(svc.embedOnce("x", "m1", " p1 "));

        verify(providers).resolveProvider("p1");
        verify(providers, never()).resolveActiveProvider();
    }

    @Test
    void embedOnceForTask_shouldUseActiveProvider_whenProviderIdBlank() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupReturnsNull(queue);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "http://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        assertNull(svc.embedOnceForTask("x", "m1", "   ", LlmQueueTaskType.EMBEDDING));

        verify(providers).resolveActiveProvider();
        verify(providers, never()).resolveProvider(anyString());
    }

    @Test
    void embedOnceForTask_shouldThrow_whenModelMissing_null() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupReturnsNull(queue);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "http://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("x", null, null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Embedding model is required"));
        verify(queue, never()).callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());
    }

    @Test
    void embedOnceForTask_shouldThrow_whenModelMissing_blank() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupReturnsNull(queue);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "http://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("x", "   ", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Embedding model is required"));
        verify(queue, never()).callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());
    }

    @Test
    void embedOnceForTask_shouldBuildBody_andSetDefaultTimeout_andAddBearerAuthorization_whenNoAuthHeader() throws Exception {
        MockHttpUrl.enqueue(200, """
                {"data":[{"embedding":[0.1,0.2]}],"usage":{"prompt_tokens":2}}
                """);
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        AtomicReference<LlmCallQueueService.UsageMetrics> mNull = new AtomicReference<>();
        AtomicReference<LlmCallQueueService.UsageMetrics> mRes = new AtomicReference<>();
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), mNull, mRes);

        Map<String, String> extraHeaders = new LinkedHashMap<>();
        extraHeaders.put(null, "x");
        extraHeaders.put(" ", "x");
        extraHeaders.put("X-Null", null);
        extraHeaders.put("X-Trace", "t1");

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1/", "ak", extraHeaders, 0, -1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("a\\b\"\n\t", "  m1  ", null, null);

        assertNotNull(r);
        assertEquals(2, r.dims());
        assertEquals("m1", r.model());
        assertEquals(0.1f, r.vector()[0], 1e-6);
        assertEquals(0.2f, r.vector()[1], 1e-6);

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertEquals("mockhttp://api.example.com/v1/embeddings", req.url().toString());
        assertEquals("application/json", req.headers().get("Content-Type"));
        assertEquals("t1", req.headers().get("X-Trace"));
        assertEquals("Bearer ak", req.headers().get("Authorization"));

        String body = new String(req.body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"model\":\"m1\""));
        assertTrue(body.contains("\"input\":\"a\\\\b\\\"\\n\\t\""));

        assertNull(mNull.get());
        assertNotNull(mRes.get());
        assertEquals(2, mRes.get().promptTokens());
        assertEquals(2, mRes.get().totalTokens());
    }

    @Test
    void embedOnceForTask_shouldWrap_whenActiveProviderNull_andProviderIdNull() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        when(queue.callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    LlmCallQueueService.CheckedTaskSupplier<Object> supplier = (LlmCallQueueService.CheckedTaskSupplier<Object>) inv.getArgument(5);
                    return supplier.get(null);
                });

        when(providers.resolveActiveProvider()).thenReturn(null);

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().startsWith("Embedding failed:"));
    }

    @Test
    void embedOnceForTask_shouldHandleNullTimeout_extraHeadersNull_apiKeyNull_andTotalTokensOnly() throws Exception {
        MockHttpUrl.enqueue(200, """
                {"data":[{"embedding":[0.1]}],"usage":{"total_tokens":5}}
                """);

        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        AtomicReference<LlmCallQueueService.UsageMetrics> mNull = new AtomicReference<>();
        AtomicReference<LlmCallQueueService.UsageMetrics> mRes = new AtomicReference<>();
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), mNull, mRes);

        when(providers.resolveActiveProvider()).thenReturn(new ResolvedProvider(
                "pA",
                "OPENAI_COMPAT",
                "mockhttp://api.example.com/v1",
                null,
                "chat",
                "embed",
                Map.of(),
                null,
                null,
                null
        ));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING);
        assertNotNull(r);

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertNull(req.headers().get("Authorization"));

        assertNull(mNull.get());
        assertNotNull(mRes.get());
        assertNull(mRes.get().promptTokens());
        assertEquals(5, mRes.get().totalTokens());
    }

    @Test
    void embedOnceForTask_shouldThrow_whenStatusBelow200_andIncludeBody() throws Exception {
        MockHttpUrl.enqueue(199, "boom199");

        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), new AtomicReference<>(), new AtomicReference<>());

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Embedding upstream error HTTP 199: boom199"));
    }

    @Test
    void embedOnceForTask_shouldIgnoreUsage_whenNotObject() throws Exception {
        MockHttpUrl.enqueue(200, """
                {"data":[{"embedding":[0.1]}],"usage":1}
                """);

        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        AtomicReference<LlmCallQueueService.UsageMetrics> mNull = new AtomicReference<>();
        AtomicReference<LlmCallQueueService.UsageMetrics> mRes = new AtomicReference<>();
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), mNull, mRes);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING);
        assertNotNull(r);

        assertNull(mNull.get());
        assertNull(mRes.get());
    }

    @Test
    void embedOnceForTask_shouldNotOverrideAuthorization_whenExtraHeadersContainAuthorization() throws Exception {
        MockHttpUrl.enqueue(200, """
                {"data":[{"embedding":[0.1]}]}
                """);
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), new AtomicReference<>(), new AtomicReference<>());

        Map<String, String> extraHeaders = new LinkedHashMap<>();
        extraHeaders.put("  Authorization  ", "ApiKey x");
        extraHeaders.put("X-Trace", "t2");

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", extraHeaders, 1234, 5678));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("q", "m1", null, LlmQueueTaskType.EMBEDDING);
        assertNotNull(r);

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("ApiKey x", req.headers().get("  Authorization  "));
        assertEquals("t2", req.headers().get("X-Trace"));
        assertNull(req.headers().get("Authorization"));
    }

    @Test
    void embedOnceForTask_shouldNotAddBearerAuthorization_whenApiKeyBlank() throws Exception {
        MockHttpUrl.enqueue(200, """
                {"data":[{"embedding":[0.1]}]}
                """);
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), new AtomicReference<>(), new AtomicReference<>());

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "   ", Map.of("X-Trace", "t3"), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("q", "m1", null, LlmQueueTaskType.EMBEDDING);
        assertNotNull(r);

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("t3", req.headers().get("X-Trace"));
        assertNull(req.headers().get("Authorization"));
    }

    @Test
    void embedOnceForTask_non2xx_errorStreamNull_shouldThrowWithoutBody() throws Exception {
        MockHttpUrl.enqueue(500, null);
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), new AtomicReference<>(), new AtomicReference<>());

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Upstream returned HTTP 500 without body"));
    }

    @Test
    void embedOnceForTask_non2xx_errorStreamNonNull_shouldThrowWithBody() throws Exception {
        MockHttpUrl.enqueue(500, "boom");
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), new AtomicReference<>(), new AtomicReference<>());

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Embedding upstream error HTTP 500: boom"));
    }

    @Test
    void embedOnceForTask_usageParseFail_shouldStillReturnVector_andUsageMetricsNull() throws Exception {
        MockHttpUrl.enqueue(200, "NOT_JSON {\"x\":1, \"embedding\":[1.0, 2.0]}");
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        AtomicReference<LlmCallQueueService.UsageMetrics> mNull = new AtomicReference<>();
        AtomicReference<LlmCallQueueService.UsageMetrics> mRes = new AtomicReference<>();
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), mNull, mRes);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING);

        assertNotNull(r);
        assertEquals(2, r.vector().length);
        assertEquals(1.0f, r.vector()[0], 1e-6);
        assertEquals(2.0f, r.vector()[1], 1e-6);
        assertNull(mNull.get());
        assertNull(mRes.get());
    }

    @Test
    void embedOnceForTask_vecParseFail_shouldThrow() throws Exception {
        MockHttpUrl.enqueue(200, "{\"data\":[{\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}");
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);
        stubCallDedupExecutesSupplier(queue, mock(LlmCallQueueService.TaskHandle.class), new AtomicReference<>(), new AtomicReference<>());

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Failed to parse embedding vector"));
    }

    @Test
    void embedOnceForTask_shouldSwallowReportOutputException_andStillReturnResult_andCoverEllipsis() throws Exception {
        MockHttpUrl.enqueue(200, "{\"data\":[{\"embedding\":[1,2,3,4,5,6,7,8,9,10,11,12,13]}]}");
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doThrow(new RuntimeException("boom")).when(task).reportOutput(anyString());

        stubCallDedupExecutesSupplier(queue, task, new AtomicReference<>(), new AtomicReference<>());

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "mockhttp://api.example.com/v1", "ak", Map.of(), 1, 1));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        AiEmbeddingService.EmbeddingResult r = svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING);
        assertNotNull(r);
        assertEquals(13, r.vector().length);
    }

    @Test
    void embedOnceForTask_shouldWrapRuntimeException_fromSupplier_asIOException() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        when(providers.resolveActiveProvider()).thenReturn(provider("pA", "http://api.example.com/v1", "ak", Map.of(), 1, 1));
        when(queue.callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any()))
                .thenThrow(new RuntimeException("boom"));

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask("hi", "m1", null, LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().contains("Embedding failed: boom"));
    }

    @Test
    void embedOnceForTask_shouldWrapNullProvider_path_asIOException() throws Exception {
        AiProvidersConfigService providers = mock(AiProvidersConfigService.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        when(queue.callDedup(any(), any(), any(), anyInt(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    LlmCallQueueService.CheckedTaskSupplier<Object> supplier = (LlmCallQueueService.CheckedTaskSupplier<Object>) inv.getArgument(5);
                    return supplier.get(null);
                });

        when(providers.resolveProvider("p1")).thenReturn(null);

        AiEmbeddingService svc = new AiEmbeddingService(providers, queue);
        IOException ex = assertThrows(IOException.class, () -> svc.embedOnceForTask(null, "m1", " p1 ", LlmQueueTaskType.EMBEDDING));
        assertTrue(ex.getMessage().startsWith("Embedding failed:"));
    }
}
