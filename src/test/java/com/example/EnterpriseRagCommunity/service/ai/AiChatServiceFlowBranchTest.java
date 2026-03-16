package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;

class AiChatServiceFlowBranchTest {
    @Test
    void streamChat_should_write_meta_with_negative_session_id_when_dry_run_and_session_id_null() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(null);
        req.setMessage("hi");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: meta"));
        assertTrue(body.contains("\"sessionId\":-"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamChat_should_ignore_blank_and_non_data_sse_lines() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("");
            consumer.onLine("event: ping");
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(null);
        req.setMessage("hi");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("ok"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamChat_deepThink_false_should_ignore_reasoning_content() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(null);
        req.setMessage("hi");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("c1"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamChat_should_write_error_and_done_when_persist_fails() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenThrow(new RuntimeException("boom"));

        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, mock(QaTurnsRepository.class));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: error"));
        assertTrue(body.contains("event: done"));
        assertTrue(body.contains("数据操作失败"));
    }

    @Test
    void chatOnce_should_accumulate_sse_deltas_to_content() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"hi\"}");
            consumer.onLine("data: {\"content\":\"!\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);

        AiChatResponseDTO dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertEquals("hi!", dto.getContent());
        assertTrue(dto.getLatencyMs() != null && dto.getLatencyMs() >= 0);
    }

    @Test
    void chatOnce_deepThink_should_wrap_reasoning_with_think_tags_and_autoclose() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);

        AiChatResponseDTO dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertEquals("<think>r1</think>c1", dto.getContent());
    }

    @Test
    void chatOnce_deepThink_should_autoclose_when_only_reasoning_present() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r-only\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);

        AiChatResponseDTO dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertEquals("<think>r-only</think>", dto.getContent());
    }

    @Test
    void chatOnce_should_ignore_blank_and_non_data_sse_lines() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("");
            consumer.onLine("event: ping");
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        AiChatResponseDTO dto = service.chatOnce(req, 1L);
        assertEquals("ok", dto.getContent());
    }

    @Test
    void chatOnce_deepThink_false_should_ignore_reasoning_content() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        AiChatResponseDTO dto = service.chatOnce(req, 1L);
        assertEquals("c1", dto.getContent());
    }

    @Test
    void regenerateOnce_should_only_include_history_before_question_message() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question file_asset_id=1");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());

        QaMessagesEntity before1 = new QaMessagesEntity();
        before1.setId(1L);
        before1.setRole(MessageRole.USER);
        before1.setContent("before1");
        before1.setCreatedAt(LocalDateTime.now().minusSeconds(30));

        QaMessagesEntity after1 = new QaMessagesEntity();
        after1.setId(99L);
        after1.setRole(MessageRole.ASSISTANT);
        after1.setContent("after1");
        after1.setCreatedAt(LocalDateTime.now().plusSeconds(10));

        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(before1, q, after1));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        AiChatResponseDTO dto = service.regenerateOnce(10L, req, 1L);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        List<ChatMessage> msgs = cap.getValue();

        String merged = msgs.toString();
        assertTrue(merged.contains("before1"));
        assertTrue(!merged.contains("after1"));
        assertEquals(true, dto.getContent().contains("ok"));
    }

    @Test
    void regenerateOnce_deepThink_should_autoclose_when_only_reasoning_present() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"regen-r\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);
        req.setHistoryLimit(20);

        AiChatResponseDTO dto = service.regenerateOnce(10L, req, 1L);
        assertEquals("<think>regen-r</think>", dto.getContent());
    }

    @Test
    void regenerateOnce_deepThink_false_should_ignore_reasoning_content() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);
        AiChatResponseDTO dto = service.regenerateOnce(10L, req, 1L);
        assertEquals("c1", dto.getContent());
    }

    @Test
    void streamRegenerate_should_write_meta_and_done() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: meta"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamRegenerate_deepThink_should_emit_autoclosed_think_delta() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"stream-r\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: delta"));
        assertTrue(body.contains("<think>stream-r"));
        assertTrue(body.contains("</think>"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamRegenerate_should_ignore_blank_and_non_data_sse_lines() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("");
            consumer.onLine("event: ping");
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("ok"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void chatOnce_should_throw_when_req_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        assertThrows(IllegalArgumentException.class, () -> service.chatOnce(null, 1L));
    }

    @Test
    void chatOnce_should_throw_when_current_user_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> service.chatOnce(req, null));
    }

    @Test
    void streamChat_should_throw_when_session_not_found() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hello");
        req.setDryRun(false);
        req.setUseRag(false);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.streamChat(req, 1L, new MockHttpServletResponse()));
        assertEquals("session not found", ex.getMessage());
    }

    @Test
    void streamChat_should_throw_when_session_inactive() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hello");
        req.setDryRun(false);
        req.setUseRag(false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.streamChat(req, 1L, new MockHttpServletResponse()));
        assertEquals("session inactive", ex.getMessage());
    }

    @Test
    void chatOnce_should_throw_when_session_not_found() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hello");
        req.setDryRun(false);
        req.setUseRag(false);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.chatOnce(req, 1L));
        assertEquals("session not found", ex.getMessage());
    }

    @Test
    void chatOnce_should_throw_when_session_inactive() {
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hello");
        req.setDryRun(false);
        req.setUseRag(false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.chatOnce(req, 1L));
        assertEquals("session inactive", ex.getMessage());
    }

    @Test
    void regenerateOnce_should_throw_when_question_message_id_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.regenerateOnce(null, req, 1L));
        assertEquals("questionMessageId is required", ex.getMessage());
    }

    @Test
    void regenerateOnce_should_throw_when_req_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.regenerateOnce(10L, null, 1L));
        assertEquals("req is required", ex.getMessage());
    }

    @Test
    void regenerateOnce_should_throw_when_message_not_found() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.empty());
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.regenerateOnce(10L, req, 1L));
        assertEquals("message not found", ex.getMessage());
    }

    @Test
    void regenerateOnce_should_throw_when_session_not_found() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.regenerateOnce(10L, req, 1L));
        assertEquals("session not found", ex.getMessage());
    }

    @Test
    void regenerateOnce_should_throw_when_question_message_role_is_not_user() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.ASSISTANT);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, qaMessagesRepository, mock(QaTurnsRepository.class));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        assertThrows(IllegalArgumentException.class, () -> service.regenerateOnce(10L, req, 1L));
    }

    @Test
    void regenerateOnce_should_throw_when_session_inactive() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        assertThrows(IllegalArgumentException.class, () -> service.regenerateOnce(10L, req, 1L));
    }

    @Test
    void regenerateOnce_should_throw_when_current_user_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> service.regenerateOnce(10L, req, null));
    }

    @Test
    void streamRegenerate_should_throw_when_current_user_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> service.streamRegenerate(10L, req, null, new MockHttpServletResponse()));
    }

    @Test
    void streamRegenerate_should_throw_when_question_message_id_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.streamRegenerate(null, req, 1L, new MockHttpServletResponse()));
        assertEquals("questionMessageId is required", ex.getMessage());
    }

    @Test
    void streamRegenerate_should_throw_when_req_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.streamRegenerate(10L, null, 1L, new MockHttpServletResponse()));
        assertEquals("req is required", ex.getMessage());
    }

    @Test
    void streamRegenerate_should_throw_when_message_not_found() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.empty());
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.streamRegenerate(10L, req, 1L, new MockHttpServletResponse()));
        assertEquals("message not found", ex.getMessage());
    }

    @Test
    void streamRegenerate_should_throw_when_session_not_found() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.empty());
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.streamRegenerate(10L, req, 1L, new MockHttpServletResponse()));
        assertEquals("session not found", ex.getMessage());
    }

    @Test
    void streamRegenerate_should_throw_when_question_message_role_is_not_user() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.ASSISTANT);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        AiChatService service = buildService(mock(LlmGateway.class), mock(QaSessionsRepository.class), qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.streamRegenerate(10L, req, 1L, new MockHttpServletResponse()));
        assertEquals("只能对用户问题消息进行重新生成", ex.getMessage());
    }

    @Test
    void streamRegenerate_should_throw_when_session_inactive() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        AiChatService service = buildService(mock(LlmGateway.class), qaSessionsRepository, qaMessagesRepository, mock(QaTurnsRepository.class));
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.streamRegenerate(10L, req, 1L, new MockHttpServletResponse()));
        assertEquals("session inactive", ex.getMessage());
    }

    @Test
    void streamRegenerate_should_write_error_and_done_when_llm_throws() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("llm boom"));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: error"));
        assertTrue(body.contains("llm boom"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamChat_should_write_error_and_done_when_llm_throws() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("chat boom"));
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: error"));
        assertTrue(body.contains("chat boom"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void chatOnce_should_throw_when_llm_throws() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("once boom"));
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.chatOnce(req, 1L));
        assertEquals("once boom", ex.getMessage());
    }

    @Test
    void regenerateOnce_should_throw_when_llm_throws() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("regen boom"));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.regenerateOnce(10L, req, 1L));
        assertEquals("regen boom", ex.getMessage());
    }

    @Test
    void chatOnce_deepThink_should_ignore_reasoning_after_think_closed() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: {\"reasoning_content\":\"late\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
        AiChatService service = buildService(llmGateway, mock(QaSessionsRepository.class), mock(QaMessagesRepository.class), mock(QaTurnsRepository.class));

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);

        AiChatResponseDTO dto = service.chatOnce(req, 1L);
        assertEquals("<think>r1</think>c1", dto.getContent());
        assertTrue(!dto.getContent().contains("late"));
    }

    @Test
    void regenerateOnce_deepThink_should_ignore_reasoning_after_think_closed() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: {\"reasoning_content\":\"late\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);
        req.setHistoryLimit(20);
        AiChatResponseDTO dto = service.regenerateOnce(10L, req, 1L);
        assertEquals("<think>r1</think>c1", dto.getContent());
        assertTrue(!dto.getContent().contains("late"));
    }

    @Test
    void streamRegenerate_deepThink_should_ignore_reasoning_after_think_closed() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"content\":\"c1\"}");
            consumer.onLine("data: {\"reasoning_content\":\"late\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("<think>r1"));
        assertTrue(body.contains("</think>c1"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void regenerateOnce_should_reset_existing_turn_answer_id_when_not_dry_run() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(999L);
            return saved;
        });

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(77L);
        turn.setSessionId(3L);
        turn.setQuestionMessageId(10L);
        turn.setAnswerMessageId(88L);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.of(turn));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        AiChatResponseDTO dto = service.regenerateOnce(10L, req, 1L);
        assertEquals("ok", dto.getContent());
        verify(qaTurnsRepository, atLeastOnce()).save(any(QaTurnsEntity.class));
    }

    @Test
    void streamRegenerate_should_reset_existing_turn_answer_id_when_not_dry_run() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(998L);
            return saved;
        });

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(77L);
        turn.setSessionId(3L);
        turn.setQuestionMessageId(10L);
        turn.setAnswerMessageId(88L);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.of(turn));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: meta"));
        assertTrue(body.contains("event: done"));
        verify(qaTurnsRepository, atLeastOnce()).save(any(QaTurnsEntity.class));
    }

    @Test
    void regenerateOnce_should_include_system_history_role() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        AiChatService service = buildService(llmGateway, qaSessionsRepository, qaMessagesRepository, qaTurnsRepository);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());

        QaMessagesEntity beforeSystem = new QaMessagesEntity();
        beforeSystem.setId(2L);
        beforeSystem.setRole(MessageRole.SYSTEM);
        beforeSystem.setContent("sys-h");
        beforeSystem.setCreatedAt(LocalDateTime.now().minusSeconds(20));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(beforeSystem, q));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);
        service.regenerateOnce(10L, req, 1L);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("sys-h"));
    }

    private static AiChatService buildService(
            LlmGateway llmGateway,
            QaSessionsRepository qaSessionsRepository,
            QaMessagesRepository qaMessagesRepository,
            QaTurnsRepository qaTurnsRepository
    ) {
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        ChatRagAugmentConfigService chatRagAugmentConfigService = mock(ChatRagAugmentConfigService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);
        UsersRepository usersRepository = mock(UsersRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        PortalChatConfigService portalChatConfigService = mock(PortalChatConfigService.class);
        PortalChatConfigDTO portalCfg = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPromptCode("s");
        assistantCfg.setDeepThinkSystemPromptCode("s2");
        assistantCfg.setHistoryLimit(20);
        assistantCfg.setRagTopK(6);
        assistantCfg.setDefaultUseRag(false);
        assistantCfg.setDefaultDeepThink(false);
        assistantCfg.setDefaultStream(true);
        portalCfg.setAssistantChat(assistantCfg);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);

        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity p = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        p.setSystemPrompt("sys");
        when(promptsRepository.findByPromptCode(any())).thenReturn(Optional.of(p));

        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(new ChatContextGovernanceConfigDTO());

        ChatContextGovernanceService chatContextGovernanceService = mock(ChatContextGovernanceService.class);
        when(chatContextGovernanceService.apply(any(), any(), any(), any())).thenAnswer(inv -> {
            ChatContextGovernanceService.ApplyResult r = new ChatContextGovernanceService.ApplyResult();
            r.setMessages(inv.getArgument(3));
            r.setChanged(false);
            r.setReason("nochange");
            r.setBeforeTokens(0);
            r.setAfterTokens(0);
            r.setBeforeChars(0);
            r.setAfterChars(0);
            r.setDetail(Map.of());
            return r;
        });

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(new ContextClipConfigDTO());
        when(citationConfigService.getConfigOrDefault()).thenReturn(new CitationConfigDTO());
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(chatCfg);

        LlmModelEntity enabled = new LlmModelEntity();
        enabled.setEnabled(true);
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
            .thenReturn(List.of(enabled));

        return new AiChatService(
                llmGateway,
                llmModelRepository,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                ragRetrievalService,
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                hybridRetrievalConfigService,
                hybridRagRetrievalService,
                contextClipConfigService,
                citationConfigService,
                chatRagAugmentConfigService,
                ragContextPromptService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                postsRepository,
                qaMessageSourcesRepository,
                tokenCountService,
                usersRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                portalChatConfigService,
                promptsRepository,
                chatContextGovernanceConfigService,
                chatContextGovernanceService
        );
    }
}
