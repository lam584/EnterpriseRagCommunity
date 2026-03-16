package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
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
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;

class AiChatServiceStreamChatCoverageTest {

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

        // Mock basic config
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

        // Mock session and persistence
        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(1L);
        session.setUserId(1L); // Match the userId passed in tests
        session.setIsActive(true); // Ensure active
        
        // Use explicit matchers and any()
        when(qaSessionsRepository.findById(any())).thenReturn(java.util.Optional.of(session));
        when(qaSessionsRepository.findByIdAndUserId(any(), any())).thenReturn(java.util.Optional.of(session));
        when(qaSessionsRepository.findByIdAndUserId(eq(1L), eq(1L))).thenReturn(java.util.Optional.of(session));
        
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

        LlmModelRepository llmModelRepo = mock(LlmModelRepository.class);
        PostsRepository postsRepo = mock(PostsRepository.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepo = mock(FileAssetExtractionsRepository.class);
        PromptsRepository promptsRepo = mock(PromptsRepository.class);
        ChatContextGovernanceService chatContextGovernanceSvc = mock(ChatContextGovernanceService.class);
        when(chatContextGovernanceSvc.apply(any(), any(), any(), any())).thenAnswer(inv -> {
            List<ChatMessage> msgs = inv.getArgument(3);
            ApplyResult r = new ApplyResult();
            r.setMessages(msgs);
            return r;
        });
        
        when(ragChatPostCommentAggregationService.aggregate(any(), any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));

        when(retrievalEventsRepository.save(any())).thenAnswer(inv -> {
            RetrievalEventsEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
        
        when(llmModelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(any(), any()))
                .thenReturn(List.of(new LlmModelEntity()));

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
                postsRepo,
                qaMessageSourcesRepository,
                tokenCountService,
                usersRepo,
                fileAssetsRepo,
                fileAssetExtractionsRepo,
                portalChatConfigService,
                promptsRepo,
                chatContextGovernanceConfigService,
                chatContextGovernanceSvc
        );
    }

    private ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);

    @Test
    void testStreamChat_Basic_Debug() throws IOException {
        System.err.println("DEBUG: Starting testStreamChat_Basic_Debug");
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDryRun(false);

        doAnswer(inv -> {
            System.err.println("DEBUG: Inside chatStreamRouted mock");
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"answer\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            aiChatService.streamChat(req, 1L, response);
            System.err.println("DEBUG: streamChat finished");
        } catch (Exception e) {
            System.err.println("DEBUG: streamChat threw exception: " + e);
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    void testStreamChat_DeepThink_AutoTags() throws IOException {
        // Test auto-adding <think> tags when LLM returns reasoning without them
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDeepThink(true);
        req.setDryRun(false);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            // reasoning without <think>
            consumer.onLine("data: {\"reasoning_content\":\"thinking process\"}");
            consumer.onLine("data: {\"content\":\"final answer\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), eq(true), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        String output = response.getContentAsString();
        System.err.println("DEBUG AutoTags Output: " + output);
        assertTrue(output.contains("<think>thinking process"), "Output should contain start tag");
        assertTrue(output.contains("</think>final answer"), "Output should contain end tag");
    }

    @Test
    void testStreamChat_DeepThink_ExistingTags_Split() throws IOException {
        // Test handling of <think> tags when they are already present or split
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(1L);
        req.setMessage("test");
        req.setDeepThink(true);
        req.setDryRun(false);

        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            // reasoning with <think> start
            consumer.onLine("data: {\"reasoning_content\":\"<think>part1\"}");
            consumer.onLine("data: {\"reasoning_content\":\"part2\"}");
            // content triggers closure
            consumer.onLine("data: {\"content\":\"answer\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("prov", "model", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), eq(true), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        aiChatService.streamChat(req, 1L, response);

        String output = response.getContentAsString();
        // Should contain <think>part1part2</think>
        // Note: The code checks if reasoning starts with <think>, if so it doesn't add another one.
        // And it adds </think> when content arrives if think is open.
        assertTrue(output.contains("<think>part1"));
        assertTrue(output.contains("part2"));
        assertTrue(output.contains("</think>"));
    }
}
