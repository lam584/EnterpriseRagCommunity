package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
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

class AiChatServiceStreamChatRagAndPersistTest {
    @Test
    void streamChat_should_throw_when_current_user_id_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class));
        assertThrows(org.springframework.security.core.AuthenticationException.class, () -> {
            service.streamChat(new AiChatStreamRequest(), null, new MockHttpServletResponse());
        });
    }

    @Test
    void streamChat_should_write_meta_with_userMessageId_and_load_history_and_user_system_prompt() throws Exception {
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
        session.setTitle("t");
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        QaMessagesEntity savedUser = new QaMessagesEntity();
        savedUser.setId(10L);
        QaTurnsEntity savedTurn = new QaTurnsEntity();
        savedTurn.setId(11L);
        savedTurn.setSessionId(3L);
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenReturn(savedUser);
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenReturn(savedTurn);

        List<QaMessagesEntity> hist = new ArrayList<>();
        QaMessagesEntity skip = new QaMessagesEntity();
        skip.setId(10L);
        skip.setRole(MessageRole.USER);
        skip.setContent("skip");
        hist.add(skip);
        QaMessagesEntity sys = new QaMessagesEntity();
        sys.setId(2L);
        sys.setRole(MessageRole.SYSTEM);
        sys.setContent("sys0");
        hist.add(sys);
        QaMessagesEntity asst = new QaMessagesEntity();
        asst.setId(3L);
        asst.setRole(MessageRole.ASSISTANT);
        asst.setContent("a0");
        hist.add(asst);
        when(qaMessagesRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(hist));

        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity u = new UsersEntity();
        Map<String, Object> assistant = new HashMap<>();
        assistant.put("defaultSystemPrompt", "user-sys");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("assistant", assistant);
        Map<String, Object> md = new HashMap<>();
        md.put("preferences", prefs);
        u.setMetadata(md);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(u));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                usersRepository,
                portalCfgWithDefaults(false, null, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hi");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(null);
        req.setHistoryLimit(-1);

        AiChatStreamRequest.ImageInput img = new AiChatStreamRequest.ImageInput();
        img.setUrl("https://example.com/a.png");
        img.setMimeType("image/png");
        req.setImages(List.of(img));
        AiChatStreamRequest.FileInput f = new AiChatStreamRequest.FileInput();
        f.setFileAssetId(123L);
        f.setFileName("a.txt");
        f.setMimeType("text/plain");
        req.setFiles(List.of(f));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: meta"));
        assertTrue(body.contains("\"userMessageId\":10"));
        assertTrue(body.contains("event: done"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QaMessagesEntity> userMsgCap = ArgumentCaptor.forClass(QaMessagesEntity.class);
        verify(qaMessagesRepository, atLeastOnce()).save(userMsgCap.capture());
        QaMessagesEntity savedUserMsg = userMsgCap.getAllValues().stream()
                .filter(m -> m != null && m.getRole() == MessageRole.USER)
                .findFirst()
                .orElse(userMsgCap.getValue());
        String savedText = savedUserMsg.getContent();
        assertTrue(savedText.contains("[IMAGES]"));
        assertTrue(savedText.contains("[FILES]"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("sys0"));
        assertTrue(merged.contains("a0"));
        assertTrue(merged.contains("user-sys"));
        assertTrue(!merged.contains("skip"));
    }

    @Test
    void streamChat_should_auto_close_think_when_only_reasoning_streamed() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"r\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(true, null, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                new CitationConfigDTO(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hi");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(true);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("<think>"));
        assertTrue(body.contains("</think>"));
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
                any(org.springframework.data.jpa.domain.Specification.class),
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
                any(org.springframework.data.jpa.domain.Specification.class),
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

        ArgumentCaptor<List<RetrievalHitsEntity>> hitsCap = ArgumentCaptor.forClass(List.class);
        verify(retrievalHitsRepository).saveAll(hitsCap.capture());
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.AGG));
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.BM25));
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.VEC));
        assertTrue(hitsCap.getValue().stream().anyMatch(h -> h.getHitType() == RetrievalHitType.RERANK));

        ArgumentCaptor<List<com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity>> srcCap = ArgumentCaptor.forClass(List.class);
        verify(qaMessageSourcesRepository).saveAll(srcCap.capture());
        assertTrue(srcCap.getValue().size() == 200);

        ArgumentCaptor<HybridRetrievalConfigDTO> cfgCap = ArgumentCaptor.forClass(HybridRetrievalConfigDTO.class);
        verify(hybridRagRetrievalService).retrieve(any(), any(), cfgCap.capture(), eq(false));
        assertTrue(cfgCap.getValue().getHybridK() == 50);

        ArgumentCaptor<RagChatPostCommentAggregationService.Config> acCap = ArgumentCaptor.forClass(RagChatPostCommentAggregationService.Config.class);
        verify(ragChatPostCommentAggregationService).aggregate(any(), any(), any(), acCap.capture());
        assertTrue(acCap.getValue().getIncludePostContentPolicy() == null);

        ArgumentCaptor<ContextWindowsEntity> cwCap = ArgumentCaptor.forClass(ContextWindowsEntity.class);
        verify(contextWindowsRepository).save(cwCap.capture());
        assertTrue(cwCap.getValue().getEventId() == 99L);
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
                any(org.springframework.data.jpa.domain.Specification.class),
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
        assertTrue(!body.contains("event: rag_debug"));
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
                any(org.springframework.data.jpa.domain.Specification.class),
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
        assertTrue(dto.getAssistantMessageId() == null);
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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

    private static List<RagContextPromptService.CitationSource> buildSources(int n) {
        List<RagContextPromptService.CitationSource> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            RagContextPromptService.CitationSource s = new RagContextPromptService.CitationSource();
            s.setIndex(i);
            s.setPostId((long) i);
            s.setChunkIndex(i);
            s.setScore(Double.parseDouble(String.format(Locale.ROOT, "%.6f", 0.1 * i)));
            s.setTitle("t" + i);
            s.setUrl("u" + i);
            out.add(s);
        }
        return out;
    }

    private static DocHit docHit(Long postId, double score) {
        DocHit h = new DocHit();
        h.setPostId(postId);
        h.setScore(score);
        h.setBm25Score(score);
        h.setVecScore(score);
        h.setRerankScore(score);
        return h;
    }

    private static CitationConfigDTO citationCfgEnabled() {
        CitationConfigDTO cfg = new CitationConfigDTO();
        cfg.setEnabled(true);
        cfg.setCitationMode("SOURCES_SECTION");
        cfg.setSourcesTitle("Sources");
        cfg.setIncludeTitle(true);
        cfg.setIncludeUrl(true);
        cfg.setIncludeScore(true);
        cfg.setIncludePostId(true);
        cfg.setIncludeChunkIndex(true);
        return cfg;
    }

    private static PortalChatConfigDTO portalCfgWithDefaults(boolean defaultDeepThink, Integer historyLimit, String providerId, String model) {
        PortalChatConfigDTO portalCfg = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPromptCode("s");
        assistantCfg.setDeepThinkSystemPromptCode("s2");
        assistantCfg.setDefaultUseRag(false);
        assistantCfg.setDefaultDeepThink(defaultDeepThink);
        assistantCfg.setHistoryLimit(historyLimit);
        assistantCfg.setProviderId(providerId);
        assistantCfg.setModel(model);
        portalCfg.setAssistantChat(assistantCfg);
        return portalCfg;
    }

    private static AiChatService buildService(LlmGateway llmGateway) {
        return buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                new CitationConfigDTO(),
                new ChatContextGovernanceConfigDTO()
        );
    }

    private static AiChatService buildServiceWithOverrides(
            LlmGateway llmGateway,
            QaSessionsRepository qaSessionsRepository,
            QaMessagesRepository qaMessagesRepository,
            QaTurnsRepository qaTurnsRepository,
            UsersRepository usersRepository,
            PortalChatConfigDTO portalCfg,
            ChatRagAugmentConfigDTO chatRagCfg,
            HybridRetrievalConfigDTO hybridCfg,
            ContextClipConfigDTO contextCfg,
            CitationConfigDTO citationCfg,
            ChatContextGovernanceConfigDTO govCfg
    ) {
        return buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                usersRepository,
                portalCfg,
                chatRagCfg,
                hybridCfg,
                contextCfg,
                citationCfg,
                govCfg,
                mock(RagContextPromptService.class),
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                mock(RetrievalEventsRepository.class),
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class)
        );
    }

    private static AiChatService buildServiceWithOverrides(
            LlmGateway llmGateway,
            QaSessionsRepository qaSessionsRepository,
            QaMessagesRepository qaMessagesRepository,
            QaTurnsRepository qaTurnsRepository,
            UsersRepository usersRepository,
            PortalChatConfigDTO portalCfg,
            ChatRagAugmentConfigDTO chatRagCfg,
            HybridRetrievalConfigDTO hybridCfg,
            ContextClipConfigDTO contextCfg,
            CitationConfigDTO citationCfg,
            ChatContextGovernanceConfigDTO govCfg,
            RagContextPromptService ragContextPromptService,
            HybridRagRetrievalService hybridRagRetrievalService,
            RagCommentChatRetrievalService ragCommentChatRetrievalService,
            RagChatPostCommentAggregationService ragChatPostCommentAggregationService,
            RetrievalEventsRepository retrievalEventsRepository,
            RetrievalHitsRepository retrievalHitsRepository,
            ContextWindowsRepository contextWindowsRepository,
            QaMessageSourcesRepository qaMessageSourcesRepository
    ) {
        return buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                usersRepository,
                portalCfg,
                chatRagCfg,
                hybridCfg,
                contextCfg,
                citationCfg,
                govCfg,
                ragContextPromptService,
                hybridRagRetrievalService,
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                retrievalHitsRepository,
                contextWindowsRepository,
                qaMessageSourcesRepository,
                mock(RagPostChatRetrievalService.class)
        );
    }

    private static AiChatService buildServiceWithOverrides(
            LlmGateway llmGateway,
            QaSessionsRepository qaSessionsRepository,
            QaMessagesRepository qaMessagesRepository,
            QaTurnsRepository qaTurnsRepository,
            UsersRepository usersRepository,
            PortalChatConfigDTO portalCfg,
            ChatRagAugmentConfigDTO chatRagCfg,
            HybridRetrievalConfigDTO hybridCfg,
            ContextClipConfigDTO contextCfg,
            CitationConfigDTO citationCfg,
            ChatContextGovernanceConfigDTO govCfg,
            RagContextPromptService ragContextPromptService,
            HybridRagRetrievalService hybridRagRetrievalService,
            RagCommentChatRetrievalService ragCommentChatRetrievalService,
            RagChatPostCommentAggregationService ragChatPostCommentAggregationService,
            RetrievalEventsRepository retrievalEventsRepository,
            RetrievalHitsRepository retrievalHitsRepository,
            ContextWindowsRepository contextWindowsRepository,
            QaMessageSourcesRepository qaMessageSourcesRepository,
            RagPostChatRetrievalService ragRetrievalService
    ) {
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmModelEntity vision = new LlmModelEntity();
        vision.setEnabled(true);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(any(), any(), any(), any())).thenReturn(Optional.of(vision));
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(any(), any())).thenReturn(List.of(vision));

        ContextClipConfigService contextClipConfigService = mock(ContextClipConfigService.class);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(contextCfg);

        CitationConfigService citationConfigService = mock(CitationConfigService.class);
        when(citationConfigService.getConfigOrDefault()).thenReturn(citationCfg);

        ChatRagAugmentConfigService chatRagAugmentConfigService = mock(ChatRagAugmentConfigService.class);
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(chatRagCfg);

        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        PortalChatConfigService portalChatConfigService = mock(PortalChatConfigService.class);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);

        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity p = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        p.setSystemPrompt("sys");
        when(promptsRepository.findByPromptCode(any())).thenReturn(Optional.of(p));

        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(govCfg);

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

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        PostsRepository postsRepository = mock(PostsRepository.class);

        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);

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
