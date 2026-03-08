package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.ai.AdminAiModelProbeResultDTO;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;

class AdminAiModelProbeServiceTest {
    @Test
    void probe_should_validate_required_fields() {
        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), mock(AiRerankService.class));

        assertThrows(IllegalArgumentException.class, () -> s.probe(" ", "p", "m", null));
        assertThrows(IllegalArgumentException.class, () -> s.probe("CHAT", " ", "m", null));
        assertThrows(IllegalArgumentException.class, () -> s.probe("CHAT", "p", " ", null));
    }

    @Test
    void probe_embedding_should_handle_empty_vector() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenReturn(new AiEmbeddingService.EmbeddingResult(new float[0], 0, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("embedding", " p ", " m ", 2000L);
        assertNotNull(out);
        assertEquals(false, out.getOk());
        assertEquals("embedding 响应为空或向量为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
        assertTrue(out.getLatencyMs() != null && out.getLatencyMs() >= 0);
    }

    @Test
    void probe_embedding_should_handle_null_vector() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenReturn(new AiEmbeddingService.EmbeddingResult(null, 0, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("embedding", "p", "m", 2000L);
        assertEquals(false, out.getOk());
        assertEquals("embedding 响应为空或向量为空", out.getErrorMessage());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_embedding_should_succeed_and_fallback_model_when_result_model_null() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[] {1.0f, 2.0f}, 2, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("embedding", " p ", " m ", null);

        assertEquals(true, out.getOk());
        assertEquals(null, out.getErrorMessage());
        assertEquals("EMBEDDING", out.getKind());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_embedding_should_handle_null_result() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenReturn(null);

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("EMBEDDING", "p", "m", 2000L);

        assertEquals(false, out.getOk());
        assertEquals("embedding 响应为空或向量为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_embedding_should_use_returned_model_when_present() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p")))
                .thenReturn(new AiEmbeddingService.EmbeddingResult(new float[] {1.0f}, 1, "m-used"));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("EMBEDDING", "p", "m", 2000L);

        assertEquals(true, out.getOk());
        assertEquals("m-used", out.getUsedModel());
    }

    @Test
    void probe_rerank_should_handle_empty_results() throws Exception {
        AiRerankService rerank = mock(AiRerankService.class);
        when(rerank.rerankOnce(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyBoolean(), any()))
                .thenReturn(new AiRerankService.RerankResult(List.of(), 1, null, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), rerank);
        AdminAiModelProbeResultDTO out = s.probe("RERANK", "p", "m", 2000L);
        assertEquals(false, out.getOk());
        assertEquals("rerank 响应为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_rerank_should_handle_null_results() throws Exception {
        AiRerankService rerank = mock(AiRerankService.class);
        when(rerank.rerankOnce(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyBoolean(), any()))
                .thenReturn(new AiRerankService.RerankResult(null, 1, null, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), rerank);
        AdminAiModelProbeResultDTO out = s.probe("RERANK", "p", "m", 2000L);
        assertEquals(false, out.getOk());
        assertEquals("rerank 响应为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_chat_should_handle_blank_text() throws Exception {
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.chatOnceRouted(any(), anyString(), anyString(), anyList(), anyDouble(), anyInt(), anyList()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult("   ", null, null, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(llm, mock(AiEmbeddingService.class), mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("CHAT", "p", "m", 2000L);
        assertEquals(false, out.getOk());
        assertEquals("chat 响应为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_rerank_should_succeed_and_use_returned_provider_and_model() throws Exception {
        AiRerankService rerank = mock(AiRerankService.class);
        List<AiRerankService.RerankHit> hits = List.of(new AiRerankService.RerankHit(0, 0.98));
        when(rerank.rerankOnce(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyBoolean(), any()))
                .thenReturn(new AiRerankService.RerankResult(hits, 12, "p-used", "m-used"));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), rerank);
        AdminAiModelProbeResultDTO out = s.probe("RERANK", "p", "m", 2000L);

        assertEquals(true, out.getOk());
        assertEquals(null, out.getErrorMessage());
        assertEquals("p-used", out.getUsedProviderId());
        assertEquals("m-used", out.getUsedModel());
    }

    @Test
    void probe_rerank_should_fallback_provider_and_model_when_result_fields_null() throws Exception {
        AiRerankService rerank = mock(AiRerankService.class);
        List<AiRerankService.RerankHit> hits = List.of(new AiRerankService.RerankHit(0, 0.98));
        when(rerank.rerankOnce(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyBoolean(), any()))
                .thenReturn(new AiRerankService.RerankResult(hits, 12, null, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), rerank);
        AdminAiModelProbeResultDTO out = s.probe("RERANK", "p", "m", 2000L);

        assertEquals(true, out.getOk());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_rerank_should_handle_null_result() throws Exception {
        AiRerankService rerank = mock(AiRerankService.class);
        when(rerank.rerankOnce(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyBoolean(), any()))
                .thenReturn(null);

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), rerank);
        AdminAiModelProbeResultDTO out = s.probe("RERANK", "p", "m", 2000L);

        assertEquals(false, out.getOk());
        assertEquals("rerank 响应为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_chat_should_succeed_and_use_returned_provider_and_model() throws Exception {
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.chatOnceRouted(any(), anyString(), anyString(), anyList(), anyDouble(), anyInt(), anyList()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult("ok", "p-used", "m-used", null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(llm, mock(AiEmbeddingService.class), mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("CHAT", "p", "m", 2000L);

        assertEquals(true, out.getOk());
        assertEquals(null, out.getErrorMessage());
        assertEquals("p-used", out.getUsedProviderId());
        assertEquals("m-used", out.getUsedModel());
    }

    @Test
    void probe_chat_should_fallback_provider_and_model_when_result_fields_null() throws Exception {
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.chatOnceRouted(any(), anyString(), anyString(), anyList(), anyDouble(), anyInt(), anyList()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult("ok", null, null, null));

        AdminAiModelProbeService s = new AdminAiModelProbeService(llm, mock(AiEmbeddingService.class), mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("CHAT", "p", "m", 2000L);

        assertEquals(true, out.getOk());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_chat_should_handle_null_result() throws Exception {
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.chatOnceRouted(any(), anyString(), anyString(), anyList(), anyDouble(), anyInt(), anyList()))
                .thenReturn(null);

        AdminAiModelProbeService s = new AdminAiModelProbeService(llm, mock(AiEmbeddingService.class), mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("CHAT", "p", "m", 2000L);

        assertEquals(false, out.getOk());
        assertEquals("chat 响应为空", out.getErrorMessage());
        assertEquals("p", out.getUsedProviderId());
        assertEquals("m", out.getUsedModel());
    }

    @Test
    void probe_should_clamp_timeout_to_minimum_1000ms() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenAnswer(inv -> {
            Thread.sleep(200L);
            return new AiEmbeddingService.EmbeddingResult(new float[] {1.0f}, 1, "m");
        });

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("EMBEDDING", "p", "m", 1L);

        assertEquals(true, out.getOk());
        assertEquals(null, out.getErrorMessage());
        verify(emb).embedOnce(eq("ping"), eq("m"), eq("p"));
    }

    @Test
    void probe_should_return_failure_for_unsupported_kind() {
        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), mock(AiRerankService.class));

        AdminAiModelProbeResultDTO out = s.probe("image", "p", "m", 2000L);

        assertEquals(false, out.getOk());
        assertEquals("不支持的 kind: IMAGE", out.getErrorMessage());
        assertEquals("IMAGE", out.getKind());
    }

    @Test
    void probe_should_return_exception_message_when_upstream_throws_with_message() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenThrow(new IllegalStateException("boom"));

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("EMBEDDING", "p", "m", 2000L);

        assertEquals(false, out.getOk());
        assertTrue(out.getErrorMessage() != null && out.getErrorMessage().contains("boom"));
    }

    @Test
    void probe_should_return_exception_class_name_when_message_blank() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenThrow(new IllegalStateException());

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("EMBEDDING", "p", "m", 2000L);

        assertEquals(false, out.getOk());
        assertTrue(out.getErrorMessage() != null && out.getErrorMessage().contains("IllegalStateException"));
    }

    @Test
    void probe_should_return_timeout_on_slow_upstream() throws Exception {
        AiEmbeddingService emb = mock(AiEmbeddingService.class);
        when(emb.embedOnce(eq("ping"), eq("m"), eq("p"))).thenAnswer(inv -> {
            Thread.sleep(2000L);
            return new AiEmbeddingService.EmbeddingResult(new float[] {1.0f}, 1, "m");
        });

        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), emb, mock(AiRerankService.class));
        AdminAiModelProbeResultDTO out = s.probe("EMBEDDING", "p", "m", 1L);
        assertEquals(false, out.getOk());
        assertEquals("timeout", out.getErrorMessage());
    }

    @Test
    void helper_methods_should_cover_remaining_branches() throws Exception {
        Method buildProbeMessages = AdminAiModelProbeService.class.getDeclaredMethod("buildProbeMessages", LlmQueueTaskType.class);
        buildProbeMessages.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ChatMessage> rerankMessages = (List<ChatMessage>) buildProbeMessages.invoke(null, LlmQueueTaskType.RERANK);
        assertEquals(2, rerankMessages.size());
        @SuppressWarnings("unchecked")
        List<ChatMessage> chatMessages = (List<ChatMessage>) buildProbeMessages.invoke(null, LlmQueueTaskType.TEXT_CHAT);
        assertEquals(2, chatMessages.size());

        Method safeMessage = AdminAiModelProbeService.class.getDeclaredMethod("safeMessage", Throwable.class);
        safeMessage.setAccessible(true);
        assertEquals("error", safeMessage.invoke(null, new Object[] {null}));
        assertEquals("RuntimeException", safeMessage.invoke(null, new RuntimeException("   ")));

        Method normalizeKind = AdminAiModelProbeService.class.getDeclaredMethod("normalizeKind", String.class);
        normalizeKind.setAccessible(true);
        assertEquals("", normalizeKind.invoke(null, new Object[] {null}));
        assertEquals("CHAT", normalizeKind.invoke(null, " chat "));

        Method trimOrEmpty = AdminAiModelProbeService.class.getDeclaredMethod("trimOrEmpty", String.class);
        trimOrEmpty.setAccessible(true);
        assertEquals("", trimOrEmpty.invoke(null, new Object[] {null}));
        assertEquals("x", trimOrEmpty.invoke(null, " x "));
    }

    @Test
    void shutdown_should_close_executor_without_throwing() {
        AdminAiModelProbeService s = new AdminAiModelProbeService(mock(LlmGateway.class), mock(AiEmbeddingService.class), mock(AiRerankService.class));
        s.shutdown();
    }
}
