package com.example.EnterpriseRagCommunity.service.ai;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;

class AiChatServiceThinkingDirectiveTest {
    @Test
    void streamChat_should_append_no_think_for_namespaced_qwen3_model_when_deepThink_is_false() throws Exception {
        AiChatService service = buildService();

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDeepThink(false);
        req.setUseRag(false);
        req.setProviderId("LLM-Studio");
        req.setModel("qwen/qwen3-30b-a3b");
        req.setDryRun(true);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);

        LlmGateway llmGateway = getGatewayFromService(service);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(
                any(),
                eq("LLM-Studio"),
                eq("qwen/qwen3-30b-a3b"),
                cap.capture(),
                any(),
                any(),
                eq(false),
                any(),
                any()
        );

        List<ChatMessage> msgs = cap.getValue();
        ChatMessage last = msgs.get(msgs.size() - 1);
        String content = last.content() == null ? "" : String.valueOf(last.content());
        assertTrue(content.contains("hello"));
        assertTrue(content.contains("/no_think"));
    }

    @Test
    void streamChat_should_append_think_for_namespaced_qwen3_model_when_deepThink_is_true() throws Exception {
        AiChatService service = buildService();

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hello");
        req.setDeepThink(true);
        req.setUseRag(false);
        req.setProviderId("LLM-Studio");
        req.setModel("qwen/qwen3-30b-a3b");
        req.setDryRun(true);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);

        LlmGateway llmGateway = getGatewayFromService(service);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(
                any(),
                eq("LLM-Studio"),
                eq("qwen/qwen3-30b-a3b"),
                cap.capture(),
                any(),
                any(),
                eq(true),
                any(),
                any()
        );

        List<ChatMessage> msgs = cap.getValue();
        ChatMessage last = msgs.get(msgs.size() - 1);
        String content = last.content() == null ? "" : String.valueOf(last.content());
        assertTrue(content.contains("hello"));
        assertTrue(content.contains("/think"));
    }

    private static AiChatService buildService() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository llmModelRepository = mock(com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository.class);
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
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        ChatContextGovernanceService chatContextGovernanceService = mock(ChatContextGovernanceService.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO portalCfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO();
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPromptCode("s");
        assistantCfg.setDeepThinkSystemPromptCode("s2");
        assistantCfg.setHistoryLimit(20);
        assistantCfg.setRagTopK(6);
        assistantCfg.setDefaultUseRag(true);
        assistantCfg.setDefaultDeepThink(false);
        assistantCfg.setDefaultStream(true);
        portalCfg.setAssistantChat(assistantCfg);
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.PostComposeAssistantConfigDTO postComposeCfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        postComposeCfg.setSystemPromptCode("s");
        postComposeCfg.setDeepThinkSystemPromptCode("s2");
        postComposeCfg.setComposeSystemPromptCode("c");
        postComposeCfg.setChatHistoryLimit(20);
        portalCfg.setPostComposeAssistant(postComposeCfg);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(new ChatContextGovernanceConfigDTO());
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

    private static LlmGateway getGatewayFromService(AiChatService service) throws Exception {
        var f = AiChatService.class.getDeclaredField("llmGateway");
        f.setAccessible(true);
        return (LlmGateway) f.get(service);
    }
}
