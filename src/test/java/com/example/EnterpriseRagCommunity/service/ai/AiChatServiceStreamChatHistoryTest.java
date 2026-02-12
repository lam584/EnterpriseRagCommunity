package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
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
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;

class AiChatServiceStreamChatHistoryTest {

    @Test
    void streamChat_should_not_fail_when_history_page_content_is_unmodifiable_list() throws Exception {
        AiProperties props = new AiProperties();
        props.setApiKey(null);

        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.resolve(any())).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "test",
                "OPENAI_COMPAT",
                "http://127.0.0.1:1",
                null,
                "test-model",
                "test-emb",
                Map.<String, Object>of(),
                Map.<String, String>of(),
                1000,
                1000,
                null
        ));
        doAnswer(inv -> {
            com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(4);
            consumer.onLine("data: {\"content\":\"hi\"}");
            consumer.onLine("data: [DONE]");
            return null;
        }).when(llmGateway).chatStream(any(), any(), any(), any(), any());

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
        PortalChatConfigService portalChatConfigService = mock(PortalChatConfigService.class);
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO portalCfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO();
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.AssistantChatConfigDTO assistantCfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.AssistantChatConfigDTO();
        assistantCfg.setSystemPrompt("s");
        assistantCfg.setDeepThinkSystemPrompt("s2");
        assistantCfg.setHistoryLimit(20);
        assistantCfg.setRagTopK(6);
        assistantCfg.setDefaultUseRag(true);
        assistantCfg.setDefaultDeepThink(false);
        assistantCfg.setDefaultStream(true);
        portalCfg.setAssistantChat(assistantCfg);
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.PostComposeAssistantConfigDTO postComposeCfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.PostComposeAssistantConfigDTO();
        postComposeCfg.setSystemPrompt("s");
        postComposeCfg.setDeepThinkSystemPrompt("s2");
        postComposeCfg.setComposeSystemPrompt("c");
        postComposeCfg.setChatHistoryLimit(20);
        portalCfg.setPostComposeAssistant(postComposeCfg);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(portalCfg);

        AiChatService service = new AiChatService(
                props,
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
                portalChatConfigService
        );

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(3L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(session));

        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity m = inv.getArgument(0);
            if (m.getId() == null) m.setId(11L);
            return m;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> {
            QaTurnsEntity t = inv.getArgument(0);
            if (t.getId() == null) t.setId(8L);
            return t;
        });

        com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO cfg = new com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO();
        cfg.setEnabled(false);
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(cfg);
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(new com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO());
        when(citationConfigService.getConfigOrDefault()).thenReturn(new com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO());
        ChatRagAugmentConfigDTO chatCfg = new ChatRagAugmentConfigDTO();
        chatCfg.setEnabled(false);
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(chatCfg);
        when(ragRetrievalService.retrieve(any(String.class), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(List.of());
        when(retrievalEventsRepository.save(any())).thenAnswer(inv -> {
            var ev = inv.getArgument(0);
            if (ev instanceof com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity e && e.getId() == null) {
                e.setId(99L);
            }
            return ev;
        });

        QaMessagesEntity m1 = new QaMessagesEntity();
        m1.setId(1L);
        m1.setSessionId(3L);
        m1.setRole(MessageRole.USER);
        m1.setContent("hi");
        m1.setCreatedAt(LocalDateTime.now().minusSeconds(2));

        QaMessagesEntity m2 = new QaMessagesEntity();
        m2.setId(2L);
        m2.setSessionId(3L);
        m2.setRole(MessageRole.ASSISTANT);
        m2.setContent("hello");
        m2.setCreatedAt(LocalDateTime.now().minusSeconds(1));

        Page<QaMessagesEntity> page = mock(Page.class);
        when(page.getContent()).thenReturn(Collections.unmodifiableList(List.of(m1, m2)));
        when(qaMessagesRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("第二次对话");
        req.setHistoryLimit(20);
        req.setDryRun(true);

        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertDoesNotThrow(() -> service.streamChat(req, 1L, resp));

        String body = resp.getContentAsString();
        assertTrue(body.contains("event: meta"));
        assertTrue(body.contains("event: done"));
    }
}
