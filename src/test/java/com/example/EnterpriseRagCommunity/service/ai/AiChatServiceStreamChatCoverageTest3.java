package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

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
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
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
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceService.ApplyResult;
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

class AiChatServiceStreamChatCoverageTest3 {

    private AiChatService aiChatService;
    private LlmGateway llmGateway;
    private QaSessionsRepository qaSessionsRepository;
    private QaMessagesRepository qaMessagesRepository;
    private QaTurnsRepository qaTurnsRepository;
    private PortalChatConfigService portalChatConfigService;
    private RagContextPromptService ragContextPromptService;
    private ContextClipConfigService contextClipConfigService;
    private CitationConfigService citationConfigService;
    private ChatRagAugmentConfigService chatRagAugmentConfigService;
    private HybridRetrievalConfigService hybridRetrievalConfigService;
    private HybridRagRetrievalService hybridRagRetrievalService;
    private RagPostChatRetrievalService ragRetrievalService;
    private RagCommentChatRetrievalService ragCommentChatRetrievalService;
    private RagChatPostCommentAggregationService ragChatPostCommentAggregationService;
    private RetrievalEventsRepository retrievalEventsRepository;
    private RetrievalHitsRepository retrievalHitsRepository;
    private ContextWindowsRepository contextWindowsRepository;
    private TokenCountService tokenCountService;
    private QaMessageSourcesRepository qaMessageSourcesRepository;
    private FileAssetsRepository fileAssetsRepository;
    private FileAssetExtractionsRepository fileAssetExtractionsRepository;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LlmGateway.class);
        qaSessionsRepository = mock(QaSessionsRepository.class);
        qaMessagesRepository = mock(QaMessagesRepository.class);
        qaTurnsRepository = mock(QaTurnsRepository.class);
        portalChatConfigService = mock(PortalChatConfigService.class);
        ragContextPromptService = mock(RagContextPromptService.class);
        contextClipConfigService = mock(ContextClipConfigService.class);
        citationConfigService = mock(CitationConfigService.class);
        chatRagAugmentConfigService = mock(ChatRagAugmentConfigService.class);
        hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        ragRetrievalService = mock(RagPostChatRetrievalService.class);
        ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        ragChatPostCommentAggregationService = mock(RagChatPostCommentAggregationService.class);
        retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        retrievalHitsRepository = mock(RetrievalHitsRepository.class);
        contextWindowsRepository = mock(ContextWindowsRepository.class);
        tokenCountService = mock(TokenCountService.class);
        qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        fileAssetsRepository = mock(FileAssetsRepository.class);
        fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);

        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(new FileAssetsEntity()));
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(new FileAssetExtractionsEntity()));
        
        // Configs
        PortalChatConfigDTO portalCfg = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPromptCode("default");
        portalCfg.setAssistantChat(assistantCfg);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);
        
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(new ContextClipConfigDTO());
        when(citationConfigService.getConfigOrDefault()).thenReturn(new CitationConfigDTO());
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(new ChatRagAugmentConfigDTO());
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(new HybridRetrievalConfigDTO());
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(new ChatContextGovernanceConfigDTO());

        // Session
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(1L);
        session.setUserId(1L);
        session.setIsActive(true);
        when(qaSessionsRepository.findById(any())).thenReturn(java.util.Optional.of(session));
        when(qaSessionsRepository.findByIdAndUserId(any(), any())).thenReturn(java.util.Optional.of(session));

        // Repos
        when(qaMessagesRepository.save(any())).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e == null) e = new QaMessagesEntity();
            e.setId(1L);
            return e;
        });
        when(qaMessagesRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>()));
        when(qaTurnsRepository.save(any())).thenAnswer(inv -> {
            QaTurnsEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        when(retrievalEventsRepository.save(any())).thenAnswer(inv -> {
            RetrievalEventsEntity e = inv.getArgument(0);
            if (e == null) e = new RetrievalEventsEntity();
            e.setId(1L);
            return e;
        });

        ChatContextGovernanceService chatContextGovernanceSvc = mock(ChatContextGovernanceService.class);
        when(chatContextGovernanceSvc.apply(any(), any(), any(), any())).thenAnswer(inv -> {
            List<ChatMessage> msgs = inv.getArgument(3);
            ApplyResult r = new ApplyResult();
            r.setMessages(msgs);
            return r;
        });

        // LLM
        LlmModelRepository llmModelRepo = mock(LlmModelRepository.class);
        when(llmModelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(any(), any()))
                .thenReturn(List.of(new LlmModelEntity()));
        
        // RAG Aggregation
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));

        aiChatService = new AiChatService(
                llmGateway,
                llmModelRepo,
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
                mock(PostsRepository.class),
                qaMessageSourcesRepository,
                tokenCountService,
                mock(UsersRepository.class),
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                portalChatConfigService,
                mock(PromptsRepository.class),
                chatContextGovernanceConfigService,
                chatContextGovernanceSvc
        );
    }
    
    private ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);

    @Test
    void testStreamChat_AuthFail() {
        assertThrows(AuthenticationException.class, () -> {
            aiChatService.streamChat(new AiChatStreamRequest(), null, new MockHttpServletResponse());
        });
    }

    @Test
    void testStreamChat_DryRun() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDryRun(true);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        // Verify NO persistence
        verify(qaMessagesRepository, never()).save(any());
        verify(qaTurnsRepository, never()).save(any());
    }

    @Test
    void testStreamChat_Rag_AugmentAndComments() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setUseRag(true);
        req.setDryRun(false);

        // Augment + Comments config
        ChatRagAugmentConfigDTO augmentCfg = new ChatRagAugmentConfigDTO();
        augmentCfg.setEnabled(true);
        augmentCfg.setCommentsEnabled(true);
        augmentCfg.setIncludePostContentPolicy("ALL");
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(augmentCfg);

        // Hybrid disabled (use normal RAG)
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(new HybridRetrievalConfigDTO());
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(new ArrayList<>());
        when(ragCommentChatRetrievalService.retrieve(any(), anyInt())).thenReturn(new ArrayList<>());

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        verify(ragCommentChatRetrievalService).retrieve(any(), anyInt());
        verify(ragChatPostCommentAggregationService).aggregate(any(), any(), any(), any());
    }

    @Test
    void testStreamChat_ExceptionHandling() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDryRun(false);

        // Simulate error
        doThrow(new RuntimeException("Simulated LLM Error"))
                .when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        String output = response.getContentAsString();
        assertTrue(output.contains("event: error"));
        assertTrue(output.contains("Simulated LLM Error"));
    }
    
    @Test
    void testStreamChat_FilesInput() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDryRun(false);
        
        List<AiChatStreamRequest.FileInput> files = new ArrayList<>();
        AiChatStreamRequest.FileInput file = new AiChatStreamRequest.FileInput();
        file.setFileAssetId(123L);
        files.add(file);
        req.setFiles(files);
        
        // Mock file assets repo to return valid asset owned by user 1
        FileAssetsEntity asset = new FileAssetsEntity();
        asset.setId(123L);
        asset.setOriginalName("test.txt");
        asset.setMimeType("text/plain");
        UsersEntity owner = new UsersEntity();
        owner.setId(1L);
        asset.setOwner(owner);
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(asset));
        
        // Mock extraction
        FileAssetExtractionsEntity extraction = new FileAssetExtractionsEntity();
        extraction.setFileAssetId(123L);
        extraction.setExtractedText("Extracted content");
        extraction.setExtractStatus(com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus.READY);
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction));
        
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);
        
        // Just verify execution completes
        verify(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testStreamChat_ContextWindowLogging() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setUseRag(true);
        req.setDryRun(false);

        // Context Config with logging enabled
        ContextClipConfigDTO ctxCfg = new ContextClipConfigDTO();
        ctxCfg.setLogEnabled(true);
        ctxCfg.setLogSampleRate(1.0); // Force log
        ctxCfg.setMaxItems(5);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(ctxCfg);

        // RAG Hits
        List<RagPostChatRetrievalService.Hit> hits = new ArrayList<>();
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(100L);
        hits.add(hit);
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(hits);

        // Assemble Result
        RagContextPromptService.AssembleResult assembleResult = new RagContextPromptService.AssembleResult();
        assembleResult.setContextPrompt("Context");
        assembleResult.setChunkIds(java.util.Map.of("ids", List.of("1", "2")));
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembleResult);

        // Retrieval Event ID (needed for shouldWriteContextWindow logic? Actually check code)
        // L293: shouldPersistRetrieval(req) -> checks dryRun and maybe config.
        // If dryRun=false, it saves retrieval event.
        // retrievalEventId will be populated.

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        // Verify Context Window Saved
        verify(contextWindowsRepository).save(any());
    }

    @Test
    void testStreamChat_HistoryDisabled() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        
        // Mock Session with ContextStrategy.NONE
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(1L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        when(qaSessionsRepository.findById(any())).thenReturn(java.util.Optional.of(session));
        when(qaSessionsRepository.findByIdAndUserId(any(), any())).thenReturn(java.util.Optional.of(session));

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);
        
        // Verify no history loaded
        verify(qaMessagesRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testStreamChat_HybridTopKOverrideAndPersistHits() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("hybrid");
        req.setUseRag(true);
        req.setDryRun(false);
        req.setRagTopK(99);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        hybridCfg.setBm25K(8);
        hybridCfg.setVecK(8);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        HybridRagRetrievalService.DocHit bm = new HybridRagRetrievalService.DocHit();
        bm.setPostId(11L);
        bm.setScore(0.7);
        HybridRagRetrievalService.DocHit vc = new HybridRagRetrievalService.DocHit();
        vc.setPostId(12L);
        vc.setScore(0.8);
        HybridRagRetrievalService.DocHit fin = new HybridRagRetrievalService.DocHit();
        fin.setPostId(13L);
        fin.setScore(0.9);

        HybridRagRetrievalService.RetrieveResult result = new HybridRagRetrievalService.RetrieveResult();
        result.setBm25Hits(List.of(bm));
        result.setVecHits(List.of(vc));
        result.setFinalHits(List.of(fin));
        when(hybridRagRetrievalService.retrieve(any(), any(), any(), eq(false))).thenReturn(result);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<HybridRetrievalConfigDTO> cfgCaptor = ArgumentCaptor.forClass(HybridRetrievalConfigDTO.class);
        verify(hybridRagRetrievalService).retrieve(any(), any(), cfgCaptor.capture(), eq(false));
        assertEquals(50, cfgCaptor.getValue().getHybridK());
        verify(retrievalHitsRepository).saveAll(any());
    }

    @Test
    void testStreamChat_NormalRagTopKClampTo50() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("normal");
        req.setUseRag(true);
        req.setDryRun(false);
        req.setRagTopK(200);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(new ArrayList<>());

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<Integer> kCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(ragRetrievalService).retrieve(any(), kCaptor.capture(), any());
        assertEquals(50, kCaptor.getValue());
    }

    @Test
    void testStreamChat_AugmentInvalidPolicyFallsBackToNullPolicy() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("augment");
        req.setUseRag(true);
        req.setDryRun(false);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        ChatRagAugmentConfigDTO augmentCfg = new ChatRagAugmentConfigDTO();
        augmentCfg.setEnabled(true);
        augmentCfg.setCommentsEnabled(false);
        augmentCfg.setIncludePostContentPolicy("INVALID_POLICY");
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(augmentCfg);
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(new ArrayList<>());

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<RagChatPostCommentAggregationService.Config> cfgCaptor =
                ArgumentCaptor.forClass(RagChatPostCommentAggregationService.Config.class);
        verify(ragChatPostCommentAggregationService).aggregate(any(), any(), any(), cfgCaptor.capture());
        assertNull(cfgCaptor.getValue().getIncludePostContentPolicy());
        verify(ragCommentChatRetrievalService, never()).retrieve(any(), anyInt());
    }

    @Test
    void testStreamChat_UseRagFalseSkipsAllRetrieval() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("no-rag");
        req.setUseRag(false);
        req.setDryRun(false);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        verify(ragRetrievalService, never()).retrieve(any(), anyInt(), any());
        verify(hybridRagRetrievalService, never()).retrieve(any(), any(), any(), anyBoolean());
        verify(ragCommentChatRetrievalService, never()).retrieve(any(), anyInt());
    }

    @Test
    void testStreamChat_RetrievalEventWithoutIdSkipsSaveHits() throws IOException {
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("save-null-id");
        req.setUseRag(true);
        req.setDryRun(false);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(new ArrayList<>());

        when(retrievalEventsRepository.save(any())).thenAnswer(inv -> new RetrievalEventsEntity());

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void testStreamChat_HistoryLimitFromPortalAndSkipCurrentUserMsg() throws IOException {
        PortalChatConfigDTO portalCfg = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPromptCode("default");
        assistantCfg.setHistoryLimit(7);
        portalCfg.setAssistantChat(assistantCfg);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);

        QaMessagesEntity hist1 = new QaMessagesEntity();
        hist1.setId(1000L);
        hist1.setRole(com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole.USER);
        hist1.setContent("old-user");
        QaMessagesEntity hist2 = new QaMessagesEntity();
        hist2.setId(1001L);
        hist2.setRole(com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole.ASSISTANT);
        hist2.setContent("old-assistant");
        when(qaMessagesRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(hist1, hist2)));

        when(qaMessagesRepository.save(any())).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole.USER) {
                e.setId(1000L);
            } else if (e.getId() == null) {
                e.setId(2000L);
            }
            return e;
        });

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("new-user");
        req.setDryRun(false);

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(qaMessagesRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(7, ((PageRequest) pageableCaptor.getValue()).getPageSize());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgsCaptor = (ArgumentCaptor<List<ChatMessage>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), msgsCaptor.capture(), any(), any(), any(), any(), any());
        long duplicateCurrentUserCount = msgsCaptor.getValue().stream()
                .filter(m -> "user".equals(m.role()) && "old-user".equals(String.valueOf(m.content())))
                .count();
        assertEquals(0, duplicateCurrentUserCount);
    }

    @Test
    void testStreamChat_TemperatureFallbackWhenDeepThink() throws IOException {
        PortalChatConfigDTO portalCfg = new PortalChatConfigDTO();
        PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPromptCode("default");
        assistantCfg.setTemperature(null);
        assistantCfg.setTopP(null);
        portalCfg.setAssistantChat(assistantCfg);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("think");
        req.setDryRun(false);
        req.setDeepThink(true);
        req.setTemperature(null);
        req.setTopP(null);

        aiChatService.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<Double> tempCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> topPCaptor = ArgumentCaptor.forClass(Double.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), any(), tempCaptor.capture(), topPCaptor.capture(), eq(true), any(), any());
        assertEquals(0.2, tempCaptor.getValue());
        assertNull(topPCaptor.getValue());
    }
}
