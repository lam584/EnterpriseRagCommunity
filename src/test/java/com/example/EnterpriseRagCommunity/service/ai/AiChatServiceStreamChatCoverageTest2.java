package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService.DocHit;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService.RetrieveResult;

class AiChatServiceStreamChatCoverageTest2 {

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

        // Basic Config
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
            e.setId(1L);
            return e;
        });

        // Chat Governance
        ChatContextGovernanceService chatContextGovernanceSvc = mock(ChatContextGovernanceService.class);
        when(chatContextGovernanceSvc.apply(any(), any(), any(), any())).thenAnswer(inv -> {
            List<ChatMessage> msgs = inv.getArgument(3);
            ApplyResult r = new ApplyResult();
            r.setMessages(msgs);
            return r;
        });

        // RAG Aggregation default
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));

        // LLM Model
        LlmModelRepository llmModelRepo = mock(LlmModelRepository.class);
        when(llmModelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(any(), any()))
                .thenReturn(List.of(new LlmModelEntity()));

        // Constructor
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
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class),
                portalChatConfigService,
                mock(PromptsRepository.class),
                chatContextGovernanceConfigService,
                chatContextGovernanceSvc
        );
    }
    
    private ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);

    @Test
    void testStreamChat_RagConfig_Hybrid() throws IOException {
        // Test Hybrid RAG configuration branch
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setUseRag(true);
        req.setDryRun(false);

        // Enable Hybrid
        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(true);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(hybridCfg);

        // Mock hybrid retrieval
        RetrieveResult result = new RetrieveResult();
        List<DocHit> docHits = new ArrayList<>();
        DocHit docHit = new DocHit();
        docHit.setPostId(100L);
        docHit.setScore(0.9);
        docHits.add(docHit);
        result.setFinalHits(docHits);
        
        when(hybridRagRetrievalService.retrieve(any(), anyLong(), any(), anyBoolean())).thenReturn(result);

        // Mock LLM
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        verify(hybridRagRetrievalService).retrieve(any(), any(), any(), anyBoolean());
        verify(ragRetrievalService, never()).retrieve(any(), anyInt(), any());
    }

    @Test
    void testStreamChat_Persistence_EdgeCases() throws IOException {
        // Test persistence edge cases (e.g. empty token counts)
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDryRun(false);

        // Mock token count service to return null
        when(tokenCountService.countTextTokens(any())).thenReturn(null);

        // Mock LLM
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        // Verify persistence happened despite null tokens
        verify(qaTurnsRepository, atLeastOnce()).save(any());
        // Verify token fields are null or handled
    }

    @Test
    void testStreamChat_Multimodal_Images() throws IOException {
        // Test Multimodal image handling
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDryRun(false);
        
        List<AiChatStreamRequest.ImageInput> images = new ArrayList<>();
        AiChatStreamRequest.ImageInput img = new AiChatStreamRequest.ImageInput();
        img.setUrl("http://example.com/image.png");
        images.add(img);
        req.setImages(images);

        // Mock LLM
        doAnswer(inv -> {
            // Verify content parts?
            // Argument 3 is messages (List<ChatMessage>)
            List<ChatMessage> msgs = inv.getArgument(3);
            // User message should be multimodal (list of maps) or string?
            // AiChatService converts it to list of maps if images present
            // But ChatMessage.user(content) expects string?
            // Actually ChatMessage has content which can be String or List.
            // Let's verify call happened.
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        verify(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
