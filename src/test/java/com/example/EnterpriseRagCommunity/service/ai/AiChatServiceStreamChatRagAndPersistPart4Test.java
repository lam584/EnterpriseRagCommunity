package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
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
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService.DocHit;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;


class AiChatServiceStreamChatRagAndPersistPart4Test extends AiChatServiceStreamChatRagAndPersistTestSupport {
    @Test
    void streamRegenerate_should_throw_when_session_is_inactive() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(false);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        AiChatService service = buildServiceWithOverrides(
                mock(LlmGateway.class),
                qaSessionsRepository,
                qaMessagesRepository,
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        assertThrows(IllegalArgumentException.class, () -> service.streamRegenerate(10L, req, 1L, new MockHttpServletResponse()));
    }

    @Test
    void streamRegenerate_should_emit_error_event_when_gateway_throws() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("q");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: error"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamRegenerate_should_cover_hybrid_rerank_and_empty_stage_hits_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ans\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-h", "m-h", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("hybrid-empty");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2401L);
            return e;
        });

        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        hybridResult.setBm25Hits(null);
        hybridResult.setVecHits(null);
        hybridResult.setFinalHits(null);
        when(hybridRagRetrievalService.retrieve(anyString(), any(), any(), eq(false))).thenReturn(hybridResult);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(4);
        hybridCfg.setRerankEnabled(true);
        hybridCfg.setRerankModel("rr-model");
        hybridCfg.setRerankK(9);

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(2400L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, 20, "pid-default", "m-default");
        portalCfg.getAssistantChat().setDefaultUseRag(true);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfg,
                chatCfg,
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class)
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(true);
        req.setRagTopK(4);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));
        verify(retrievalEventsRepository).save(any(RetrievalEventsEntity.class));
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void streamRegenerate_should_cover_files_block_blank_and_routed_null_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("event: ping");
            consumer.onLine("data: {\"content\":\"routed-null\"}");
            consumer.onLine("data: [DONE]");
            return null;
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("q\n[FILES]\n- file_asset_id=999 name=x.txt mime=text/plain");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2501L);
            return e;
        });

        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid-default", "m-default"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: delta"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamRegenerate_should_cover_history_break_on_future_message_timestamp() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        LocalDateTime now = LocalDateTime.now();
        QaMessagesEntity q = messageWithRole(10L, MessageRole.USER, "q", now);
        q.setSessionId(3L);
        QaMessagesEntity old = messageWithRole(9L, MessageRole.USER, "old", now.minusMinutes(1));
        old.setSessionId(3L);
        QaMessagesEntity future = messageWithRole(11L, MessageRole.ASSISTANT, "future", now.plusMinutes(1));
        future.setSessionId(3L);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(old, future, q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2601L);
            return e;
        });

        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid-default", "m-default"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messagesCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), messagesCap.capture(), any(), any(), any(), any(), any());
        String merged = String.valueOf(messagesCap.getValue());
        assertTrue(merged.contains("old"));
        assertFalse(merged.contains("future"));
    }

    @Test
    void streamRegenerate_should_cover_think_close_guard_when_content_starts_with_close_tag() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"<think>r\"}");
            consumer.onLine("data: {\"content\":\"</think>ans\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("q");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, 20, "pid-default", "m-default");
        portalCfg.getAssistantChat().setDefaultDeepThink(true);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfg,
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("</think>ans"));
        assertFalse(body.contains("</think></think>"));
    }

    @Test
    void chatOnce_should_cover_non_hybrid_invalid_policy_and_sampling_skip_with_empty_hit_entities() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid", "m1", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setTitle("has-title");
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                if (e.getRole() == MessageRole.USER) e.setId(701L);
                if (e.getRole() == MessageRole.ASSISTANT) e.setId(702L);
            }
            return e;
        });
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(770L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        List<RagPostChatRetrievalService.Hit> postHits = new ArrayList<>();
        postHits.add(null);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(postHits);
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(postHits);

        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("   ");
        assembled.setSources(List.of());
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setPolicy(ContextWindowPolicy.TOPK);
        assembled.setBudgetTokens(10);
        assembled.setUsedTokens(5);
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid-default", "m-default");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(false);
        chatCfg.setIncludePostContentPolicy("bad-policy");
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setMaxItems(null);
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(0.0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfg,
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("ok"));
        verify(ragCommentChatRetrievalService, never()).retrieve(anyString(), anyInt());
        verify(retrievalHitsRepository, never()).saveAll(any());
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));
    }

    @Test
    void chatOnce_should_cover_routed_null_turn_null_and_empty_delta_paths() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(null);
            consumer.onLine(" ");
            consumer.onLine("event: ping");
            consumer.onLine("data: [DONE]");
            return null;
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setTitle("fixed-title");
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(801L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(802L);
            return e;
        });
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fileAssetsRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(any(), anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid-default", "m-default"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class),
                fileAssetsRepository,
                fileAssetExtractionsRepository
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("m");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setProviderId(" pid-x ");
        req.setModel(" model-y ");
        AiChatStreamRequest.ImageInput img = new AiChatStreamRequest.ImageInput();
        img.setUrl(" ");
        req.setImages(List.of(img));
        AiChatStreamRequest.FileInput f = new AiChatStreamRequest.FileInput();
        f.setFileAssetId(999L);
        req.setFiles(List.of(f));

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        verify(llmGateway).chatStreamRouted(any(), eq("pid-x"), eq("model-y"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void chatOnce_should_cover_deepthink_prefixed_reasoning_and_prefixed_close_tag_content() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"<think>r\"}");
            consumer.onLine("data: {\"content\":\"</think>c\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(true, 20, "pid", "m"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("q");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);
        var dto = service.chatOnce(req, 1L);
        assertEquals("<think>r</think>c", dto.getContent());
    }

    @Test
    void chatOnce_should_cover_normalization_change_and_sources_text_blank_paths() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"\\\"abc\\\"[1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(100L);
        hit.setChunkIndex(1);
        hit.setScore(0.5);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt(null);
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        CitationConfigDTO citationCfg = new CitationConfigDTO();

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfg,
                chatCfg,
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfg,
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("norm");
        req.setDryRun(true);
        req.setUseRag(true);
        req.setDeepThink(false);
        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("[1]"));
    }

    @Test
    void chatOnce_should_cover_additional_branch_matrix_for_defaults_and_multimodal_paths() {
        LlmGateway llmGatewayA = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"a\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-a", "m-a", null);
        }).when(llmGatewayA).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepositoryA = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepositoryA = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepositoryA = mock(QaTurnsRepository.class);
        RagPostChatRetrievalService ragRetrievalServiceA = mock(RagPostChatRetrievalService.class);
        RetrievalEventsRepository retrievalEventsRepositoryA = mock(RetrievalEventsRepository.class);
        RagContextPromptService ragContextPromptServiceA = mock(RagContextPromptService.class);
        RagChatPostCommentAggregationService ragAggA = mock(RagChatPostCommentAggregationService.class);

        QaSessionsEntity sessionA = new QaSessionsEntity();
        sessionA.setId(0L);
        sessionA.setUserId(1L);
        sessionA.setIsActive(true);
        sessionA.setContextStrategy(ContextStrategy.NONE);
        sessionA.setTitle("t");
        when(qaSessionsRepositoryA.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(sessionA));
        when(qaMessagesRepositoryA.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(9101L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(9102L);
            return e;
        });
        when(qaTurnsRepositoryA.save(any(QaTurnsEntity.class))).thenReturn(null);

        RagPostChatRetrievalService.Hit hA = new RagPostChatRetrievalService.Hit();
        hA.setPostId(1L);
        hA.setChunkIndex(1);
        hA.setType(RetrievalHitType.COMMENT_VEC);
        hA.setScore(0.1);
        when(ragRetrievalServiceA.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hA));
        when(ragAggA.aggregate(anyString(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));

        RetrievalEventsEntity evA = new RetrievalEventsEntity();
        evA.setId(null);
        when(retrievalEventsRepositoryA.save(any(RetrievalEventsEntity.class))).thenReturn(evA);
        when(ragContextPromptServiceA.assemble(anyString(), any(), any(), any())).thenReturn(null);

        PortalChatConfigDTO portalCfgA = portalCfgWithDefaults(true, 0, "pid-fallback", "m-fallback");
        portalCfgA.getAssistantChat().setDefaultUseRag(true);
        ChatRagAugmentConfigDTO chatCfgA = new ChatRagAugmentConfigDTO();
        chatCfgA.setEnabled(null);
        chatCfgA.setCommentsEnabled(null);
        HybridRetrievalConfigDTO hybridCfgA = null;
        ContextClipConfigDTO contextCfgA = new ContextClipConfigDTO();
        contextCfgA.setMaxItems(9);

        AiChatService serviceA = buildServiceWithOverrides(
                llmGatewayA,
                qaSessionsRepositoryA,
                qaMessagesRepositoryA,
                qaTurnsRepositoryA,
                mock(UsersRepository.class),
                portalCfgA,
                chatCfgA,
                hybridCfgA,
                contextCfgA,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptServiceA,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                ragAggA,
                retrievalEventsRepositoryA,
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalServiceA
        );

        AiChatStreamRequest reqA = new AiChatStreamRequest();
        reqA.setSessionId(3L);
        reqA.setMessage("a-msg");
        reqA.setDryRun(false);
        reqA.setUseRag(true);
        reqA.setRagTopK(8);
        reqA.setDeepThink(false);
        reqA.setHistoryLimit(0);
        reqA.setImages(List.of());
        reqA.setFiles(List.of());
        reqA.setTemperature(0.5);
        reqA.setTopP(0.8);
        reqA.setProviderId(" ");
        reqA.setModel(" ");
        var dtoA = serviceA.chatOnce(reqA, 1L);
        assertNotNull(dtoA);

        LlmGateway llmGatewayB = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r-b\"}");
            consumer.onLine("data: {\"content\":\"b\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-b", "m-b", null);
        }).when(llmGatewayB).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepositoryB = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepositoryB = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepositoryB = mock(QaTurnsRepository.class);
        FileAssetsRepository fileAssetsRepositoryB = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepositoryB = mock(FileAssetExtractionsRepository.class);

        QaSessionsEntity sessionB = new QaSessionsEntity();
        sessionB.setId(4L);
        sessionB.setUserId(1L);
        sessionB.setIsActive(true);
        sessionB.setContextStrategy(ContextStrategy.RECENT_N);
        sessionB.setTitle("");
        when(qaSessionsRepositoryB.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(sessionB));
        when(qaMessagesRepositoryB.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(9201L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(9202L);
            return e;
        });
        when(qaTurnsRepositoryB.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepositoryB.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        FileAssetsEntity fileAsset = new FileAssetsEntity();
        fileAsset.setId(321L);
        fileAsset.setPath("x.txt");
        fileAsset.setUrl("/uploads/x.txt");
        fileAsset.setMimeType("text/plain");
        when(fileAssetsRepositoryB.findById(321L)).thenReturn(Optional.of(fileAsset));
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setExtractedText("x-content");
        when(fileAssetExtractionsRepositoryB.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(any(), anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(extraction)));

        UsersRepository usersRepositoryB = mock(UsersRepository.class);
        UsersEntity userB = new UsersEntity();
        Map<String, Object> assistant = new HashMap<>();
        assistant.put("defaultSystemPrompt", "u-sys");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("assistant", assistant);
        Map<String, Object> md = new HashMap<>();
        md.put("preferences", prefs);
        userB.setMetadata(md);
        when(usersRepositoryB.findById(1L)).thenReturn(Optional.of(userB));

        AiChatService serviceB = buildServiceWithOverrides(
                llmGatewayB,
                qaSessionsRepositoryB,
                qaMessagesRepositoryB,
                qaTurnsRepositoryB,
                usersRepositoryB,
                portalCfgWithDefaults(true, 0, "pid-bf", "m-bf"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class),
                fileAssetsRepositoryB,
                fileAssetExtractionsRepositoryB
        );

        AiChatStreamRequest reqB = new AiChatStreamRequest();
        reqB.setSessionId(4L);
        reqB.setMessage("b-msg");
        reqB.setDryRun(false);
        reqB.setUseRag(false);
        reqB.setDeepThink(true);
        reqB.setTemperature(null);
        reqB.setTopP(null);
        AiChatStreamRequest.ImageInput img1 = new AiChatStreamRequest.ImageInput();
        img1.setUrl("https://example.com/1.png");
        AiChatStreamRequest.ImageInput img2 = new AiChatStreamRequest.ImageInput();
        img2.setUrl(" ");
        reqB.setImages(List.of(img1, img2));
        AiChatStreamRequest.FileInput fb = new AiChatStreamRequest.FileInput();
        fb.setFileAssetId(321L);
        reqB.setFiles(List.of(fb));
        var dtoB = serviceB.chatOnce(reqB, 1L);
        assertNotNull(dtoB);
        assertTrue(dtoB.getContent().contains("<think>"));
    }

    @Test
    void chatOnce_should_cover_remaining_history_rag_logging_and_empty_list_branches() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"rr\"}");
            consumer.onLine("data: {\"content\":\"cc\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-r", "m-r", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setTitle("");
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(9301L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(9302L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaMessagesEntity sameUser = new QaMessagesEntity();
        sameUser.setId(9301L);
        sameUser.setRole(MessageRole.USER);
        sameUser.setContent("skip-me");
        sameUser.setCreatedAt(LocalDateTime.now().minusSeconds(1));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sameUser)));

        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setType(RetrievalHitType.COMMENT_VEC);
        hit.setPostId(8L);
        hit.setChunkIndex(2);
        hit.setScore(0.8);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(9300L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx-r");
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setPolicy(ContextWindowPolicy.FIXED);
        assembled.setBudgetTokens(12);
        assembled.setUsedTokens(8);
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 7, "pid-rf", "m-rf");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setMaxItems(8);
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(null);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfg,
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("q-r");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setRagTopK(null);
        req.setDeepThink(true);
        req.setImages(List.of());
        req.setFiles(List.of());
        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
    }

    @Test
    void streamChat_should_cover_history_skip_self_and_non_hybrid_topk_comment_policy_branches() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"a[1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-h", "m-h", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(21L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setTitle("");
        when(qaSessionsRepository.findByIdAndUserId(21L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(2101L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2102L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaMessagesEntity sameUser = messageWithRole(2101L, MessageRole.USER, "skip-self", LocalDateTime.now().minusMinutes(3));
        QaMessagesEntity keepHist = messageWithRole(2201L, MessageRole.ASSISTANT, "keep-hist", LocalDateTime.now().minusMinutes(2));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sameUser, keepHist)));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(2100L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setType(RetrievalHitType.VEC);
        hit.setPostId(9L);
        hit.setChunkIndex(1);
        hit.setScore(0.8);
        hit.setContentText("ctx");
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));

        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx-h");
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);
        chatCfg.setCommentTopK(7);
        chatCfg.setIncludePostContentPolicy(" ");

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setMaxItems(8);
        contextCfg.setLogEnabled(false);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 9, "pid-hf", "m-hf"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                ragCommentChatRetrievalService,
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(21L);
        req.setMessage("q-h");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setRagTopK(5);
        req.setDeepThink(false);
        req.setImages(List.of());
        req.setFiles(List.of());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), msgCap.capture(), any(), any(), any(), any(), any());
        String merged = msgCap.getValue().toString();
        assertTrue(merged.contains("keep-hist"));
        assertFalse(merged.contains("skip-self"));
    }

    @Test
    void streamChat_should_cover_context_prompt_null_log_sample_rate_null_and_selected_null() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"answer [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-c", "m-c", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(22L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(22L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(2201L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2202L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(2200L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(10L);
        hit.setChunkIndex(1);
        hit.setScore(0.7);
        hit.setContentText("ctx");
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt(null);
        assembled.setSources(buildSources(1));
        assembled.setSelected(null);
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        assembled.setUsedTokens(3);
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(null);
        contextCfg.setMaxItems(6);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid-cf", "m-cf"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(22L);
        req.setMessage("q-c");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setRagTopK(null);
        service.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<ContextWindowsEntity> cwCap = ArgumentCaptor.forClass(ContextWindowsEntity.class);
        verify(contextWindowsRepository).save(cwCap.capture());
        assertEquals(0, cwCap.getValue().getSelectedItems());
    }

    @Test
    void streamChat_should_cover_files_block_blank_and_request_temperature_topP_overrides() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-f", "m-f", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        when(fileAssetsRepository.findById(330L)).thenReturn(Optional.empty());
        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(any(), anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(true, 20, "pid-ff", "m-ff"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class),
                fileAssetsRepository,
                fileAssetExtractionsRepository
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setDryRun(true);
        req.setMessage("q-f");
        req.setUseRag(false);
        req.setDeepThink(true);
        req.setTemperature(0.61);
        req.setTopP(0.73);
        AiChatStreamRequest.FileInput f = new AiChatStreamRequest.FileInput();
        f.setFileAssetId(330L);
        req.setFiles(List.of(f));
        req.setImages(List.of());

        service.streamChat(req, 1L, new MockHttpServletResponse());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> tempCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> topPCap = ArgumentCaptor.forClass(Double.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), msgCap.capture(), tempCap.capture(), topPCap.capture(), any(), any(), any());
        assertEquals(0.61, tempCap.getValue());
        assertEquals(0.73, topPCap.getValue());
        assertTrue(msgCap.getValue().toString().contains("q-f"));
        assertFalse(msgCap.getValue().toString().contains("Files:"));
    }

    @Test
    void streamChat_should_cover_history_system_role_mapping_branch() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-s", "m-s", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(23L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(23L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(2301L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2302L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaMessagesEntity histSystem = messageWithRole(2305L, MessageRole.SYSTEM, "hist-system-only", LocalDateTime.now().minusMinutes(2));
        QaMessagesEntity histAssistant = messageWithRole(2306L, MessageRole.ASSISTANT, "hist-assistant", LocalDateTime.now().minusMinutes(1));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(histSystem, histAssistant)));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 10, "pid-sf", "m-sf"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(23L);
        req.setMessage("q-s");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        service.streamChat(req, 1L, new MockHttpServletResponse());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), msgCap.capture(), any(), any(), any(), any(), any());
        String merged = msgCap.getValue().toString();
        assertTrue(merged.contains("hist-system-only"));
    }

    @Test
    void streamChat_should_cover_rag_context_assemble_null_branch() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid-rn", "m-rn", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(31L);
        hit.setChunkIndex(1);
        hit.setScore(0.8);
        hit.setContentText("ctx");
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(null);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid-rn", "m-rn");
        portalCfg.getAssistantChat().setDefaultUseRag(true);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfg,
                chatCfg,
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setDryRun(true);
        req.setMessage("q-rn");
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));
    }
}
