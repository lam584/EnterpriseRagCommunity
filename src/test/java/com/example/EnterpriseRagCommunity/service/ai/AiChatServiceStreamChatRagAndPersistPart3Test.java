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


class AiChatServiceStreamChatRagAndPersistPart3Test extends AiChatServiceStreamChatRagAndPersistTestSupport {
    @Test
    void chatOnce_should_cover_null_rag_configs_and_null_retrieval_event_id_path() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(" ");
            consumer.onLine("event: ping");
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
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(41L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(42L);
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

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 0, "pid", "m1");
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
        req.setDeepThink(false);
        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void regenerateOnce_should_cover_null_rag_configs_and_null_retrieval_event_id_path() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(" ");
            consumer.onLine("event: ping");
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
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(52L);
            return e;
        });
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
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

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 0, "pid", "m1");
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

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(null);
        req.setDeepThink(false);
        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void streamRegenerate_should_cover_null_rag_configs_and_null_retrieval_event_id_path() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(" ");
            consumer.onLine("event: ping");
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

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setContent("question");
        q.setCreatedAt(LocalDateTime.now());
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(62L);
            return e;
        });

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));
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

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 0, "pid", "m1");
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

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(null);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: done"));
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void chatOnce_should_cover_history_roles_and_files_block_with_image_url_filter() {
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
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(71L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(72L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaMessagesEntity skipUser = new QaMessagesEntity();
        skipUser.setId(71L);
        skipUser.setRole(MessageRole.USER);
        skipUser.setContent("skip-user");
        QaMessagesEntity histUser = new QaMessagesEntity();
        histUser.setId(2L);
        histUser.setRole(MessageRole.USER);
        histUser.setContent("u1");
        QaMessagesEntity histAsst = new QaMessagesEntity();
        histAsst.setId(3L);
        histAsst.setRole(MessageRole.ASSISTANT);
        histAsst.setContent("a1");
        QaMessagesEntity histSys = new QaMessagesEntity();
        histSys.setId(4L);
        histSys.setRole(MessageRole.SYSTEM);
        histSys.setContent("s1");
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(skipUser, histUser, histAsst, histSys)));

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("a.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/a.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setFileAssetId(10L);
        extraction.setExtractedText("FILE-TEXT");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class),
                fileAssetsRepository,
                fileAssetExtractionsRepository
        );

        AiChatStreamRequest.ImageInput blank = new AiChatStreamRequest.ImageInput();
        blank.setUrl(" ");
        blank.setMimeType("image/png");
        AiChatStreamRequest.ImageInput ok = new AiChatStreamRequest.ImageInput();
        ok.setUrl("https://example.com/ok.png");
        ok.setMimeType("image/png");
        AiChatStreamRequest.FileInput fi = new AiChatStreamRequest.FileInput();
        fi.setFileAssetId(10L);
        fi.setFileName("a.txt");
        fi.setMimeType("text/plain");

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hello");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(5);
        req.setImages(List.of(blank, ok));
        req.setFiles(List.of(fi));

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("ok"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("u1"));
        assertTrue(merged.contains("a1"));
        assertTrue(merged.contains("s1"));
        assertTrue(merged.contains("image_url"));
        verify(retrievalEventsRepository, never()).save(any());
    }

    @Test
    void regenerateOnce_should_cover_history_cutoff_files_block_and_sampling_skip() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ans\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);
        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("q\n[FILES]\n- file_asset_id=10 name=a.txt mime=text/plain");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        QaMessagesEntity beforeUser = new QaMessagesEntity();
        beforeUser.setId(1L);
        beforeUser.setRole(MessageRole.USER);
        beforeUser.setContent("u-before");
        beforeUser.setCreatedAt(q.getCreatedAt().minusSeconds(10));
        QaMessagesEntity beforeAsst = new QaMessagesEntity();
        beforeAsst.setId(2L);
        beforeAsst.setRole(MessageRole.ASSISTANT);
        beforeAsst.setContent("a-before");
        beforeAsst.setCreatedAt(q.getCreatedAt().minusSeconds(9));
        QaMessagesEntity beforeSys = new QaMessagesEntity();
        beforeSys.setId(3L);
        beforeSys.setRole(MessageRole.SYSTEM);
        beforeSys.setContent("s-before");
        beforeSys.setCreatedAt(q.getCreatedAt().minusSeconds(8));
        QaMessagesEntity after = new QaMessagesEntity();
        after.setId(4L);
        after.setRole(MessageRole.USER);
        after.setContent("after");
        after.setCreatedAt(q.getCreatedAt().plusSeconds(10));
        List<QaMessagesEntity> regenHistory = new ArrayList<>();
        regenHistory.add(null);
        regenHistory.add(beforeUser);
        regenHistory.add(beforeAsst);
        regenHistory.add(beforeSys);
        regenHistory.add(after);
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(regenHistory);
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(82L);
            return e;
        });
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(99L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(100L);
        hit.setChunkIndex(2);
        hit.setScore(0.9);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        assembled.setSources(List.of());
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("a.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/a.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setFileAssetId(10L);
        extraction.setExtractedText("FILE-TEXT-2");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction));

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(0.0);
        contextCfg.setMaxItems(4);
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
                ragRetrievalService,
                fileAssetsRepository,
                fileAssetExtractionsRepository
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setHistoryLimit(10);

        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("ans"));
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("u-before"));
        assertTrue(merged.contains("a-before"));
        assertTrue(merged.contains("s-before"));
        assertTrue(merged.contains("file_asset_id=10"));
    }

    @Test
    void streamRegenerate_should_cover_history_roles_and_files_block_path() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"s-ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("stream-q\n[FILES]\n- file_asset_id=10 name=b.txt mime=text/plain");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        List<QaMessagesEntity> streamRegenHistory = new ArrayList<>();
        streamRegenHistory.add(null);
        streamRegenHistory.add(messageWithRole(21L, MessageRole.USER, "u1", q.getCreatedAt().minusSeconds(3)));
        streamRegenHistory.add(messageWithRole(22L, MessageRole.ASSISTANT, "a1", q.getCreatedAt().minusSeconds(2)));
        streamRegenHistory.add(messageWithRole(23L, MessageRole.SYSTEM, "s1", q.getCreatedAt().minusSeconds(1)));
        streamRegenHistory.add(q);
        streamRegenHistory.add(messageWithRole(24L, MessageRole.USER, "after", q.getCreatedAt().plusSeconds(2)));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(streamRegenHistory);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("b.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/b.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setFileAssetId(10L);
        extraction.setExtractedText("STREAM-FILE-TEXT");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction));

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
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

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        req.setHistoryLimit(6);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("u1"));
        assertTrue(merged.contains("a1"));
        assertTrue(merged.contains("s1"));
        assertTrue(merged.contains("file_asset_id=10"));
    }

    @Test
    void streamChat_should_cover_non_hybrid_default_k_and_files_block_path() throws Exception {
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
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(201L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(202L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(301L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(100L);
        hit.setChunkIndex(1);
        hit.setScore(0.7);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of(hit));

        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setContextPrompt("ctx");
        ar.setSources(buildSources(1));
        ar.setSelected(List.of());
        ar.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(ar);

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("f.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/f.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setFileAssetId(10L);
        extraction.setExtractedText("F-TEXT");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction));

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(0.0);
        contextCfg.setMaxItems(null);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(false);
        chatCfg.setIncludePostContentPolicy("invalid-policy");
        chatCfg.setDebugEnabled(true);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        portalCfg.getAssistantChat().setRagTopK(null);

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
                ragRetrievalService,
                fileAssetsRepository,
                fileAssetExtractionsRepository
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("hello");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setHistoryLimit(5);
        AiChatStreamRequest.FileInput fi = new AiChatStreamRequest.FileInput();
        fi.setFileAssetId(10L);
        req.setFiles(List.of(fi));
        AiChatStreamRequest.ImageInput imgBlank = new AiChatStreamRequest.ImageInput();
        imgBlank.setUrl(" ");
        AiChatStreamRequest.ImageInput imgOk = new AiChatStreamRequest.ImageInput();
        imgOk.setUrl("https://example.com/i.png");
        req.setImages(List.of(imgBlank, imgOk));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: delta"));
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("event: done"));
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));
        verify(ragCommentChatRetrievalService, never()).retrieve(anyString(), anyInt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        assertTrue(cap.getValue() != null && !cap.getValue().isEmpty());
    }

    @Test
    void streamRegenerate_should_cover_non_hybrid_default_k_and_debug_sources_path() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"regen [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
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

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("regen-q\n[FILES]\n- file_asset_id=10 name=r.txt mime=text/plain");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(
                messageWithRole(1L, MessageRole.USER, "u0", q.getCreatedAt().minusSeconds(3)),
                messageWithRole(2L, MessageRole.ASSISTANT, "a0", q.getCreatedAt().minusSeconds(2)),
                messageWithRole(3L, MessageRole.SYSTEM, "s0", q.getCreatedAt().minusSeconds(1)),
                q
        ));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(402L);
            return e;
        });
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(401L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(8L);
        hit.setChunkIndex(1);
        hit.setScore(0.8);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of(hit));

        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setContextPrompt("ctx-r");
        ar.setSources(buildSources(1));
        ar.setSelected(List.of());
        ar.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(ar);

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(10L);
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        asset.setOriginalName("r.txt");
        asset.setMimeType("text/plain");
        asset.setUrl("https://x/r.txt");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setFileAssetId(10L);
        extraction.setExtractedText("R-TEXT");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction));

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(0.0);
        contextCfg.setMaxItems(null);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(false);
        chatCfg.setIncludePostContentPolicy("invalid-policy");
        chatCfg.setDebugEnabled(true);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        portalCfg.getAssistantChat().setRagTopK(null);

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
                ragRetrievalService,
                fileAssetsRepository,
                fileAssetExtractionsRepository
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setHistoryLimit(5);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: rag_debug"));
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("event: done"));
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));
        verify(ragCommentChatRetrievalService, never()).retrieve(anyString(), anyInt());
    }

    @Test
    void regenerateOnce_should_cover_non_hybrid_default_k_with_comments_and_context_log_save() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"rr\"}");
            consumer.onLine("data: {\"content\":\"</think>ok [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
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

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("regen-default-k");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(
                messageWithRole(1L, MessageRole.SYSTEM, "sys-h", q.getCreatedAt().minusSeconds(5)),
                messageWithRole(2L, MessageRole.USER, "u-h", q.getCreatedAt().minusSeconds(4)),
                q
        ));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(502L);
            return e;
        });

        QaTurnsEntity turn = new QaTurnsEntity();
        turn.setId(7L);
        turn.setQuestionMessageId(10L);
        turn.setAnswerMessageId(99L);
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.of(turn));
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(501L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService.Hit postHit = new RagPostChatRetrievalService.Hit();
        postHit.setPostId(12L);
        postHit.setChunkIndex(2);
        postHit.setScore(0.88);
        postHit.setType(RetrievalHitType.POST);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(postHit));

        RagCommentChatRetrievalService.Hit commentHit = new RagCommentChatRetrievalService.Hit();
        commentHit.setCommentId(99L);
        commentHit.setPostId(12L);
        commentHit.setChunkIndex(1);
        commentHit.setScore(0.5);
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of(commentHit));

        RagPostChatRetrievalService.Hit aggHit = new RagPostChatRetrievalService.Hit();
        aggHit.setPostId(12L);
        aggHit.setChunkIndex(2);
        aggHit.setScore(0.9);
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of(aggHit));

        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setContextPrompt("ctx-r1");
        ar.setPolicy(ContextWindowPolicy.FIXED);
        ar.setBudgetTokens(100);
        ar.setUsedTokens(60);
        ar.setSelected(List.of());
        ar.setDropped(List.of());
        ar.setChunkIds(Map.of("post", 12, "chunk", 2));
        ar.setSources(buildSources(1));
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(ar);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);
        contextCfg.setMaxItems(null);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);
        chatCfg.setCommentTopK(null);
        chatCfg.setIncludePostContentPolicy("always");

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        portalCfg.getAssistantChat().setRagTopK(null);

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

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(true);
        req.setHistoryLimit(6);

        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("ok"));
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
        verify(retrievalHitsRepository).saveAll(any());
    }

    @Test
    void chatOnce_should_cover_null_chat_rag_cfg_and_null_context_cfg_paths() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"chat-null-cfg\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
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
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(801L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(802L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(
                anyQaMessageSpec(),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(810L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService.Hit postHit = new RagPostChatRetrievalService.Hit();
        postHit.setPostId(9L);
        postHit.setChunkIndex(1);
        postHit.setScore(0.91);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(postHit));
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of(postHit));

        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setContextPrompt("ctx-null-cfg");
        ar.setSources(buildSources(1));
        ar.setSelected(List.of());
        ar.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(ar);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        portalCfg.getAssistantChat().setRagTopK(null);

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
        req.setMessage("q-null-cfg");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("chat-null-cfg"));
        verify(ragCommentChatRetrievalService).retrieve(anyString(), eq(20));
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));
    }

    @Test
    void regenerateOnce_should_skip_history_after_question_timestamp_with_null_context_cfg() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"regen-null-cfg\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
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

        LocalDateTime qTime = LocalDateTime.now();
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(qTime);
        q.setContent("regen-question");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(
                messageWithRole(1L, MessageRole.SYSTEM, "before-q", qTime.minusSeconds(2)),
                messageWithRole(2L, MessageRole.USER, "after-q", qTime.plusSeconds(1)),
                messageWithRole(3L, MessageRole.ASSISTANT, "later-q", qTime.plusSeconds(2))
        ));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(902L);
            return e;
        });
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(901L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService.Hit postHit = new RagPostChatRetrievalService.Hit();
        postHit.setPostId(18L);
        postHit.setChunkIndex(2);
        postHit.setScore(0.86);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(postHit));
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of(postHit));

        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setContextPrompt("ctx-regen-null-cfg");
        ar.setSources(buildSources(1));
        ar.setSelected(List.of());
        ar.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(ar);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        portalCfg.getAssistantChat().setRagTopK(null);

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
        req.setHistoryLimit(20);

        var dto = service.regenerateOnce(10L, req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("regen-null-cfg"));
        verify(ragCommentChatRetrievalService).retrieve(anyString(), eq(20));
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("before-q"));
        assertFalse(merged.contains("after-q"));
        assertFalse(merged.contains("later-q"));
    }

    @Test
    void chatOnce_should_cover_hybrid_topk_override_and_partial_retrieval_hits_matrix() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"chat-matrix [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(1001L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(1002L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(1010L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        DocHit bm25Hit = new DocHit();
        bm25Hit.setPostId(null);
        bm25Hit.setPostIds(List.of(77L));
        bm25Hit.setBm25Score(0.31);

        DocHit vecHit = new DocHit();
        vecHit.setPostId(88L);
        vecHit.setVecScore(0.22);

        DocHit rerankHit = new DocHit();
        rerankHit.setPostId(null);
        rerankHit.setPostIds(List.of(99L));
        rerankHit.setRerankScore(null);
        rerankHit.setScore(null);

        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        List<DocHit> bm25Hits = new ArrayList<>();
        bm25Hits.add(null);
        bm25Hits.add(bm25Hit);
        hybridResult.setBm25Hits(bm25Hits);
        hybridResult.setVecHits(List.of(vecHit));
        hybridResult.setFinalHits(List.of(rerankHit));
        when(hybridRagRetrievalService.retrieve(anyString(), any(), any(), eq(false))).thenReturn(hybridResult);

        RagCommentChatRetrievalService.Hit commentHit = new RagCommentChatRetrievalService.Hit();
        commentHit.setPostId(99L);
        commentHit.setCommentId(501L);
        commentHit.setScore(null);
        List<RagCommentChatRetrievalService.Hit> commentHits = new ArrayList<>();
        commentHits.add(null);
        commentHits.add(commentHit);
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(commentHits);

        RagPostChatRetrievalService.Hit agg1 = new RagPostChatRetrievalService.Hit();
        agg1.setPostId(99L);
        agg1.setScore(null);
        RagPostChatRetrievalService.Hit agg2 = new RagPostChatRetrievalService.Hit();
        agg2.setPostId(100L);
        agg2.setScore(0.7);
        List<RagPostChatRetrievalService.Hit> aggHits = new ArrayList<>();
        aggHits.add(null);
        aggHits.add(agg1);
        aggHits.add(agg2);
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(aggHits);

        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt(" ");
        assembled.setSelected(null);
        assembled.setDropped(null);
        assembled.setSources(buildSources(1));
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(3);
        hybridCfg.setBm25K(2);
        hybridCfg.setVecK(2);
        hybridCfg.setRerankEnabled(true);
        hybridCfg.setRerankK(2);
        hybridCfg.setRerankModel("rr");

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(true);
        chatCfg.setCommentsEnabled(true);
        chatCfg.setCommentTopK(0);
        chatCfg.setIncludePostContentPolicy(" ");

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
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
                mock(QaMessageSourcesRepository.class),
                mock(RagPostChatRetrievalService.class)
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("matrix");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setRagTopK(9);
        req.setDeepThink(false);

        var dto = service.chatOnce(req, 1L);
        assertNotNull(dto);
        assertTrue(dto.getContent().contains("chat-matrix"));
        verify(ragCommentChatRetrievalService).retrieve(anyString(), eq(1));
        verify(retrievalHitsRepository).saveAll(any());
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));

        ArgumentCaptor<HybridRetrievalConfigDTO> hybridCfgCap = ArgumentCaptor.forClass(HybridRetrievalConfigDTO.class);
        verify(hybridRagRetrievalService).retrieve(anyString(), any(), hybridCfgCap.capture(), eq(false));
        assertEquals(9, hybridCfgCap.getValue().getHybridK());
    }

    @Test
    void streamRegenerate_should_cover_hybrid_sources_null_fields_and_topk_override() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"stream-matrix [1] [2]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);

        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("stream-q");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(1202L);
            return e;
        });
        when(qaTurnsRepository.findByQuestionMessageId(10L)).thenReturn(Optional.empty());
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(1201L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        DocHit h1 = new DocHit();
        h1.setPostId(1L);
        h1.setScore(0.3);
        DocHit h2 = new DocHit();
        h2.setPostId(2L);
        h2.setScore(0.6);
        HybridRagRetrievalService.RetrieveResult hybridResult = new HybridRagRetrievalService.RetrieveResult();
        List<DocHit> bm25Hits = new ArrayList<>();
        bm25Hits.add(null);
        bm25Hits.add(h1);
        hybridResult.setBm25Hits(bm25Hits);
        hybridResult.setVecHits(List.of(h2));
        hybridResult.setFinalHits(List.of(h1, h2));
        when(hybridRagRetrievalService.retrieve(anyString(), any(), any(), eq(false))).thenReturn(hybridResult);

        RagContextPromptService.CitationSource sourceNullText = new RagContextPromptService.CitationSource();
        sourceNullText.setIndex(1);
        sourceNullText.setPostId(null);
        sourceNullText.setChunkIndex(null);
        sourceNullText.setScore(null);
        sourceNullText.setTitle(null);
        sourceNullText.setUrl(null);
        RagContextPromptService.CitationSource sourceFull = new RagContextPromptService.CitationSource();
        sourceFull.setIndex(2);
        sourceFull.setPostId(2L);
        sourceFull.setChunkIndex(9);
        sourceFull.setScore(0.66);
        sourceFull.setTitle("t2");
        sourceFull.setUrl("u2");
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx-s");
        List<RagContextPromptService.CitationSource> sources = new ArrayList<>();
        sources.add(null);
        sources.add(sourceNullText);
        sources.add(sourceFull);
        assembled.setSources(sources);
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setHybridK(3);

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
        req.setDeepThink(false);
        req.setRagTopK(11);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("\"title\":\"\""));
        assertTrue(body.contains("\"url\":\"\""));
        assertTrue(body.contains("event: done"));
        verify(retrievalHitsRepository).saveAll(any());

        ArgumentCaptor<HybridRetrievalConfigDTO> hybridCfgCap = ArgumentCaptor.forClass(HybridRetrievalConfigDTO.class);
        verify(hybridRagRetrievalService).retrieve(anyString(), any(), hybridCfgCap.capture(), eq(false));
        assertEquals(11, hybridCfgCap.getValue().getHybridK());
    }

    @Test
    void streamRegenerate_should_cover_deepthink_defaults_user_prompt_and_dryrun_rag_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(null);
            consumer.onLine(" ");
            consumer.onLine("event: ping");
            consumer.onLine("data: {\"reasoning_content\":\"<think>r\"}");
            consumer.onLine("data: {\"content\":\"</think>ans\"}");
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

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(null);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        UsersRepository usersRepository = mock(UsersRepository.class);
        UsersEntity user = new UsersEntity();
        Map<String, Object> assistant = new HashMap<>();
        assistant.put("defaultSystemPrompt", "user-sys-regen");
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("assistant", assistant);
        Map<String, Object> md = new HashMap<>();
        md.put("preferences", prefs);
        user.setMetadata(md);
        when(usersRepository.findById(1L)).thenReturn(Optional.of(user));

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of());

        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        when(ragCommentChatRetrievalService.retrieve(anyString(), anyInt())).thenReturn(List.of());

        RagChatPostCommentAggregationService ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        when(ragChatPostCommentAggregationService.aggregate(anyString(), any(), any(), any())).thenReturn(List.of());

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, null, "pid-default", "m-default");
        portalCfg.getAssistantChat().setDefaultUseRag(true);
        portalCfg.getAssistantChat().setHistoryLimit(0);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                mock(QaTurnsRepository.class),
                usersRepository,
                portalCfg,
                null,
                null,
                null,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
                mock(HybridRagRetrievalService.class),
                ragCommentChatRetrievalService,
                ragChatPostCommentAggregationService,
                retrievalEventsRepository,
                mock(RetrievalHitsRepository.class),
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(true);
        req.setUseRag(true);
        req.setDeepThink(null);
        req.setHistoryLimit(0);
        req.setProviderId(" req-provider ");
        req.setModel(" req-model ");

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: delta"));
        assertTrue(body.contains("ans"));
        assertTrue(body.contains("event: done"));
        verify(retrievalEventsRepository, never()).save(any());
    }

    @Test
    void streamRegenerate_should_cover_normalization_context_log_and_tokens_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"\\\"abc\\\"[1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult(
                    "pid",
                    "m-token",
                    new LlmCallQueueService.UsageMetrics(12, 7, 19, null)
            );
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("norm-q");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2201L);
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

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(101L);
        hit.setChunkIndex(1);
        hit.setScore(0.88);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        assembled.setUsedTokens(null);
        assembled.setSelected(null);
        assembled.setDropped(null);
        assembled.setChunkIds(Map.of());
        assembled.setSources(buildSources(1));
        when(ragContextPromptService.assemble(anyString(), any(), any(), any())).thenReturn(assembled);

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(2200L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);
        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(1.0);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m-default");
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
                contextCfg,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                mock(RetrievalHitsRepository.class),
                contextWindowsRepository,
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        req.setHistoryLimit(5);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("event: done"));
        verify(contextWindowsRepository).save(any(ContextWindowsEntity.class));
    }

    @Test
    void streamRegenerate_should_cover_non_hybrid_event_with_empty_hits_path() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"only-text\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("pid", "m0", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.USER);
        q.setCreatedAt(LocalDateTime.now());
        q.setContent("empty-hits");
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));
        when(qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(3L)).thenReturn(List.of(q));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(2301L);
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

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        when(ragRetrievalService.retrieve(anyString(), anyInt(), any())).thenReturn(List.of());

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(2300L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
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
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatRegenerateStreamRequest req = new AiChatRegenerateStreamRequest();
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamRegenerate(10L, req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void streamRegenerate_should_throw_when_question_message_is_not_user() {
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaMessagesEntity q = new QaMessagesEntity();
        q.setId(10L);
        q.setSessionId(3L);
        q.setRole(MessageRole.ASSISTANT);
        when(qaMessagesRepository.findById(10L)).thenReturn(Optional.of(q));

        AiChatService service = buildServiceWithOverrides(
                mock(LlmGateway.class),
                mock(QaSessionsRepository.class),
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
}
