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


class AiChatServiceStreamChatRagAndPersistPart2Test extends AiChatServiceStreamChatRagAndPersistTestSupport {
    @Test
    void streamChat_should_cover_provider_model_trim_path() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("provider-return", "model-return", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid-default", "model-default"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setDryRun(true);
        req.setMessage("m");
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setProviderId(" p-x ");
        req.setModel(" m-y ");
        service.streamChat(req, 1L, new MockHttpServletResponse());

        verify(llmGateway).chatStreamRouted(any(), eq("p-x"), eq("m-y"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void streamChat_should_cover_hybrid_enabled_with_null_result_and_valid_policy() throws Exception {
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
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(12L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(12L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(151L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(152L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(205L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(5);
        hybridCfg.setBm25K(2);
        hybridCfg.setVecK(2);
        hybridCfg.setRerankEnabled(false);

        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenReturn(null);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(false);
        chatCfg.setIncludePostContentPolicy("ALWAYS");

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of());

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                chatCfg,
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class)
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(12L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        service.streamChat(req, 1L, new MockHttpServletResponse());
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void chatOnce_should_persist_messages_and_update_empty_session_title_when_dry_run_is_false() {
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

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        session.setTitle("   ");
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaSessionsRepository.save(any(QaSessionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(10L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(20L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(
                anyQaMessageSpec(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, 20, "pid", "qwen3-xxx");
        portalCfg.getAssistantChat().setTemperature(null);
        portalCfg.getAssistantChat().setTopP(null);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfg,
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                new CitationConfigDTO(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("x".repeat(80));
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(true);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("ok"));
        verify(qaSessionsRepository).save(any(QaSessionsEntity.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Double> tempCap = ArgumentCaptor.forClass(Double.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), msgCap.capture(), tempCap.capture(), any(), any(), any(), any());
        assertTrue(tempCap.getValue() != null && tempCap.getValue() == 0.2);
        assertTrue(msgCap.getValue().toString().toLowerCase(Locale.ROOT).contains("/think"));
    }

    @Test
    void streamChat_should_cover_hybrid_rag_and_sources_and_persist_sources_with_cap() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 250; i++) {
                sb.append(" [").append(i).append(']');
            }
            consumer.onLine("data: {\"content\":\"ans" + sb + "\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        QaMessagesEntity savedUser = new QaMessagesEntity();
        savedUser.setId(10L);
        QaMessagesEntity savedAsst = new QaMessagesEntity();
        savedAsst.setId(20L);
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getRole() == MessageRole.USER) return savedUser;
            if (e.getRole() == MessageRole.ASSISTANT) return savedAsst;
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(
                anyQaMessageSpec(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsEntity evSaved = new RetrievalEventsEntity();
        evSaved.setId(99L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(evSaved);

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setPolicy(com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy.HYBRID);
        assembled.setUsedTokens(123);
        assembled.setContextPrompt("CTX");
        assembled.setChunkIds(Map.of("a", 1));
        assembled.setSources(buildSources(300));
        assembled.setSelected(List.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        hybridResult.setFinalHits(List.of(docHit(1L, 0.1), docHit(2L, 0.2)));
        hybridResult.setBm25Hits(List.of(docHit(1L, 0.1)));
        hybridResult.setVecHits(List.of(docHit(2L, 0.2)));
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenReturn(hybridResult);

        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagPostChatRetrievalService.Hit agg = new RagPostChatRetrievalService.Hit();
        agg.setPostId(1L);
        agg.setScore(0.9);
        agg.setContentText("preview");
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of(agg));

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);
        chatCfg.setDebugEnabled(true);
        chatCfg.setDebugMaxChars(200);
        chatCfg.setIncludePostContentPolicy("bad_policy");

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);
        contextCfg.setMaxItems(1);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(6);
        hybridCfg.setBm25K(1);
        hybridCfg.setVecK(1);
        hybridCfg.setRerankEnabled(true);
        hybridCfg.setRerankK(1);
        hybridCfg.setRerankModel("rm");

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 5, "pid", "m1"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                hybridRagRetrievalService,
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                qaMessageSourcesRepository
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setRagTopK(999);
        req.setDeepThink(false);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: rag_debug"));
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("event: done"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), msgCap.capture(), any(), any(), any(), any(), any());
        assertTrue(msgCap.getValue().toString().contains("CTX"));

        ArgumentCaptor<List<RetrievalHitsEntity>> hitsCap = listCaptor();
        verify(retrievalHitsRepository).saveAll(hitsCap.capture());
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.AGG));
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.BM25));
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.VEC));
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.RERANK));

        ArgumentCaptor<List<com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity>> srcCap = listCaptor();
        verify(qaMessageSourcesRepository).saveAll(srcCap.capture());
        assertEquals(200, srcCap.getValue().size());

        ArgumentCaptor<HybridRetrievalConfigDTO> cfgCap = ArgumentCaptor.forClass(HybridRetrievalConfigDTO.class);
        verify(hybridRagRetrievalService).retrieve(any(), any(), cfgCap.capture(), eq(false));
        assertEquals(50, cfgCap.getValue().getHybridK());

        ArgumentCaptor<RagChatPostCommentAggregationService.Config> acCap = ArgumentCaptor.forClass(RagChatPostCommentAggregationService.Config.class);
        verify(ragChatPostCommentAggregationService).aggregate(any(), any(), any(), acCap.capture());
        assertNull(acCap.getValue().getIncludePostContentPolicy());

        ArgumentCaptor<ContextWindowsEntity> cwCap = ArgumentCaptor.forClass(ContextWindowsEntity.class);
        verify(contextWindowsRepository).save(cwCap.capture());
        assertEquals(99L, cwCap.getValue().getEventId());
    }

    @Test
    void streamChat_should_cover_vec_rag_without_augment_and_skip_debug_when_max_chars_is_zero() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit h = new RagPostChatRetrievalService.Hit();
        h.setPostId(1L);
        h.setScore(0.1);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(h));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt(" ");
        assembled.setSources(buildSources(1));
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);
        chatCfg.setDebugEnabled(true);
        chatCfg.setDebugMaxChars(0);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        CitationConfigDTO citationCfg = citationCfgEnabled();

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(
                anyQaMessageSpec(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalEventsEntity evSaved = new RetrievalEventsEntity();
        evSaved.setId(1L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(evSaved);

        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 1, "pid", "m1"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfg,
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
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertFalse(body.contains("event: rag_debug"));
        verify(contextWindowsRepository, never()).save(any());
    }

    @Test
    void chatOnce_should_cover_hybrid_rag_paths_and_return_citation_sources() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"answer [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(10L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(20L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(
                anyQaMessageSpec(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsEntity evSaved = new RetrievalEventsEntity();
        evSaved.setId(99L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(evSaved);

        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        hybridResult.setFinalHits(List.of(docHit(1L, 0.9), docHit(2L, 0.7)));
        hybridResult.setBm25Hits(List.of(docHit(1L, 0.9)));
        hybridResult.setVecHits(List.of(docHit(2L, 0.7)));
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenReturn(hybridResult);

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagPostChatRetrievalService.Hit agg = new RagPostChatRetrievalService.Hit();
        agg.setPostId(1L);
        agg.setScore(0.99);
        agg.setContentText("ctx");
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of(agg));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setPolicy(com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy.HYBRID);
        assembled.setUsedTokens(10);
        assembled.setChunkIds(Map.of("k", 1));
        assembled.setSources(buildSources(3));
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(2);
        hybridCfg.setBm25K(1);
        hybridCfg.setVecK(1);
        hybridCfg.setRerankEnabled(true);
        hybridCfg.setRerankK(1);
        hybridCfg.setRerankModel("r");

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                mock(QaMessageSourcesRepository.class)
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("answer"));
        assertTrue(dto.getSources() != null && !dto.getSources().isEmpty());
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
    }

    @Test
    void regenerateOnce_should_cover_hybrid_rag_and_turn_rebind_paths() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r\"}");
            consumer.onLine("data: {\"content\":\"a [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question file_asset_id=1");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(20L);
            return e;
        });

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(99L);
        turn.setSessionId(3L);
        turn.setQuestionMessageId(10L);
        turn.setAnswerMessageId(123L);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.of(turn));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity evSaved = new RetrievalEventsEntity();
        evSaved.setId(66L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(evSaved);

        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        hybridResult.setFinalHits(List.of(docHit(1L, 0.9)));
        hybridResult.setBm25Hits(List.of(docHit(1L, 0.9)));
        hybridResult.setVecHits(List.of(docHit(1L, 0.9)));
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenReturn(hybridResult);

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSources(buildSources(2));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(2);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(false);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(true, 20, "pid", "m1"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class)
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(true);
        req.setHistoryLimit(20);

        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("<think>"));
        assertTrue(dto.getSources() != null && !dto.getSources().isEmpty());
        verify(qaTurnsRepository, org.mockito.Mockito.atLeastOnce()).save(any(QaTurnsEntity.class));
        verify(retrievalHitsRepository).saveAll(any());
    }

    @Test
    void streamRegenerate_should_cover_hybrid_rag_stream_and_emit_sources_event() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"rr\"}");
            consumer.onLine("data: {\"content\":\"answer [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(21L);
            return e;
        });

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(1L);
        turn.setSessionId(3L);
        turn.setQuestionMessageId(10L);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.of(turn));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(7L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        hybridResult.setFinalHits(List.of(docHit(1L, 0.9)));
        hybridResult.setBm25Hits(List.of(docHit(1L, 0.9)));
        hybridResult.setVecHits(List.of(docHit(1L, 0.9)));
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenReturn(hybridResult);

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSources(buildSources(2));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(3);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(false);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(true, 20, "pid", "m1"),
                chatCfg,
                hybridCfg,
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                qaMessageSourcesRepository
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(true);
        req.setHistoryLimit(20);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: meta"));
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("event: done"));
        verify(retrievalHitsRepository).saveAll(any());
    }

    @Test
    void chatOnce_should_continue_when_rag_retrieval_throws_and_dryrun_true() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"fallback\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenThrow(new RuntimeException("rag fail"));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class)
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setUseRag(true);
        req.setDryRun(true);
        req.setDeepThink(false);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("fallback"));
    }

    @Test
    void chatOnce_should_continue_when_retrieval_hit_persist_fails() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"fallback [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setTitle("t");
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(401L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(402L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(403L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);
        when(retrievalHitsRepository.saveAll(any())).thenThrow(new RuntimeException("hit log fail"));

        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(100L);
        hit.setChunkIndex(1);
        hit.setScore(0.7);
        hit.setContentText("ctx");
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of(hit));

        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setContextPrompt("ctx");
        ar.setSources(buildSources(1));
        ar.setSelected(List.of());
        ar.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(ar);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                chatCfg,
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setUseRag(true);
        req.setDryRun(false);
        req.setDeepThink(false);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("fallback"));
        assertFalse(dto.getContent().contains("fallback [1]"));
        assertEquals(1, dto.getSources().size());
        verify(retrievalHitsRepository).saveAll(any());
    }

    @Test
    void regenerateOnce_should_support_dryrun_and_useRag_false() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"regen\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

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

        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        assertNull(dto.getAssistantMessageId());
        assertTrue(dto.getContent().contains("regen"));
    }

    @Test
    void streamRegenerate_should_continue_when_rag_retrieval_throws() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenThrow(new RuntimeException("rag fail"));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                hybridRagRetrievalService,
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class)
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(true);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: delta"));
        assertTrue(body.contains("event: done"));
    }

    @Test
    void regenerateOnce_should_cover_non_hybrid_rag_and_new_turn_creation_paths() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ans [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of());
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(33L);
            return e;
        });

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit postHit = new RagPostChatRetrievalService.Hit();
        postHit.setPostId(1L);
        postHit.setChunkIndex(1);
        postHit.setScore(0.9);
        postHit.setContentText("ctx");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(postHit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(88L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
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
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setRagTopK(3);
        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("ans"));
        verify(qaTurnsRepository, atLeastOnce()).save(any(QaTurnsEntity.class));
    }

    @Test
    void streamRegenerate_should_cover_non_hybrid_augment_and_context_log_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"stream [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of());
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(66L);
            return e;
        });

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit p1 = new RagPostChatRetrievalService.Hit();
        p1.setPostId(1L);
        p1.setChunkIndex(1);
        p1.setScore(0.8);
        p1.setContentText("post");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(p1));

        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagCommentChatRetrievalService.Hit c1 = new RagCommentChatRetrievalService.Hit();
        c1.setPostId(1L);
        c1.setCommentId(2L);
        c1.setScore(0.7);
        when(ragCommentChatRetrievalService.retrieve(any(), anyInt())).thenReturn(List.of(c1));

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagPostChatRetrievalService.Hit agg = new RagPostChatRetrievalService.Hit();
        agg.setPostId(1L);
        agg.setChunkIndex(1);
        agg.setScore(0.9);
        agg.setContentText("agg");
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of(agg));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(90L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);
        chatCfg.setDebugEnabled(true);
        chatCfg.setIncludePostContentPolicy("invalid-policy");

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
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

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setRagTopK(2);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: rag_debug"));
        assertTrue(body.contains("event: sources"));
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
        verify(retrievalHitsRepository).saveAll(any());
    }

    @Test
    void chatOnce_should_cover_non_hybrid_augment_and_context_log_paths() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"chat [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(11L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(12L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(71L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit p1 = new RagPostChatRetrievalService.Hit();
        p1.setPostId(1L);
        p1.setChunkIndex(1);
        p1.setScore(0.8);
        p1.setContentText("post");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(p1));

        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagCommentChatRetrievalService.Hit c1 = new RagCommentChatRetrievalService.Hit();
        c1.setPostId(1L);
        c1.setCommentId(2L);
        c1.setScore(0.7);
        when(ragCommentChatRetrievalService.retrieve(any(), anyInt())).thenReturn(List.of(c1));

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagPostChatRetrievalService.Hit agg = new RagPostChatRetrievalService.Hit();
        agg.setPostId(1L);
        agg.setChunkIndex(1);
        agg.setScore(0.9);
        agg.setContentText("agg");
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of(agg));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
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
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("chat"));
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
    }

    @Test
    void streamChat_should_cover_non_hybrid_augment_and_context_log_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"stream chat [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(11L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(12L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(72L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit p1 = new RagPostChatRetrievalService.Hit();
        p1.setPostId(1L);
        p1.setChunkIndex(1);
        p1.setScore(0.8);
        p1.setContentText("post");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(p1));

        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagCommentChatRetrievalService.Hit c1 = new RagCommentChatRetrievalService.Hit();
        c1.setPostId(1L);
        c1.setCommentId(2L);
        c1.setScore(0.7);
        when(ragCommentChatRetrievalService.retrieve(any(), anyInt())).thenReturn(List.of(c1));

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagPostChatRetrievalService.Hit agg = new RagPostChatRetrievalService.Hit();
        agg.setPostId(1L);
        agg.setChunkIndex(1);
        agg.setScore(0.9);
        agg.setContentText("agg");
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of(agg));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("CTX");
        assembled.setSources(buildSources(1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);
        chatCfg.setDebugEnabled(true);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
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
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: rag_debug"));
        assertTrue(body.contains("event: done"));
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
    }

    @Test
    void streamChat_should_cover_null_rag_configs_and_null_retrieval_event_id_path() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(" ");
            consumer.onLine("event: ping");
            consumer.onLine("data: {\"reasoning_content\":\"r\"}");
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(31L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(32L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(new RetrievalEventsEntity());
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), anyBoolean())).thenReturn(null);
        when(ragCommentChatRetrievalService.retrieve(any(), anyInt())).thenReturn(List.of());
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenReturn(List.of());

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setBm25K(2);
        hybridCfg.setVecK(3);
        hybridCfg.setHybridK(4);
        hybridCfg.setRerankEnabled(false);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, 0, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfg,
                null,
                hybridCfg,
                null,
                null,
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                hybridRagRetrievalService,
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class)
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(null);
        req.setDeepThink(true);
        AiChatStreamRequest.ImageInput img = new AiChatStreamRequest.ImageInput();
        img.setUrl(" ");
        img.setMimeType("image/png");
        req.setImages(List.of(img));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);

        String body = resp.getContentAsString();
        assertTrue(body.contains("event: done"));
        verify(retrievalHitsRepository, never()).saveAll(any());
    }
}
