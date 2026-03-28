package com.example.EnterpriseRagCommunity.service.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void streamChat_should_handle_existing_think_wrappers_and_late_reasoning_after_close() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"reasoning_content\":\"<think>r1\"}");
            consumer.onLine("data: {\"content\":\"</think>c1\"}");
            consumer.onLine("data: {\"reasoning_content\":\"late\"}");
            consumer.onLine("data: {\"content\":\"c2\"}");
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
                citationCfgEnabled(),
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
        assertTrue(body.contains("<think>r1"));
        assertTrue(body.contains("</think>c1"));
        assertTrue(body.contains("c2"));
        assertTrue(!body.contains("</think></think>"));
    }

    @Test
    void streamChat_should_normalize_quoted_citations_and_emit_sources_with_null_fields() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"\\\"证据\\\"[1]\"}");
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(61L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(62L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(123L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(1L);
        hit.setChunkIndex(1);
        hit.setScore(0.7);
        hit.setContentText("d");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.CitationSource s1 = new RagContextPromptService.CitationSource();
        s1.setIndex(1);
        s1.setPostId(null);
        s1.setCommentId(77L);
        s1.setChunkIndex(null);
        s1.setScore(null);
        s1.setTitle(null);
        s1.setUrl(null);
        s1.setSnippet(null);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        List<RagContextPromptService.CitationSource> sources = new ArrayList<>();
        sources.add(null);
        sources.add(s1);
        assembled.setSources(sources);
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

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
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(3L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: sources"));
        assertTrue(body.contains("\"postId\":null"));
        assertTrue(body.contains("\"commentId\":77"));
        assertTrue(body.contains("\"chunkIndex\":null"));
        assertTrue(body.contains("\"score\":null"));
        assertTrue(body.contains("\"title\":\"\""));
        assertTrue(body.contains("\"url\":\"\""));
        assertTrue(body.contains("\"snippet\":\"\""));
        ArgumentCaptor<QaMessagesEntity> msgCap = ArgumentCaptor.forClass(QaMessagesEntity.class);
        verify(qaMessagesRepository, atLeastOnce()).save(msgCap.capture());
        boolean hasCitationAssistant = msgCap.getAllValues().stream()
                .anyMatch(m -> m != null
                        && m.getRole() == MessageRole.ASSISTANT
                        && m.getContent() != null
                        && m.getContent().contains("[1]"));
        assertTrue(hasCitationAssistant);
    }

    @Test
    void streamChat_should_skip_sources_text_when_citation_disabled() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ans [1]\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(4L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(4L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(71L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(72L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(124L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(2L);
        hit.setChunkIndex(1);
        hit.setScore(0.8);
        hit.setContentText("d");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.CitationSource s1 = new RagContextPromptService.CitationSource();
        s1.setIndex(1);
        s1.setTitle("t1");
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        assembled.setSources(List.of(s1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        CitationConfigDTO citationCfg = new CitationConfigDTO();
        citationCfg.setEnabled(false);

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
                citationCfg,
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(4L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(!body.contains("event: sources"));
        assertTrue(!body.contains("Sources："));
    }

    @Test
    void streamChat_should_cover_multimodal_images_with_all_invalid_urls() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setMessage("hi");
        req.setDryRun(true);
        req.setUseRag(false);
        req.setDeepThink(false);
        AiChatStreamRequest.ImageInput i1 = new AiChatStreamRequest.ImageInput();
        i1.setUrl(" ");
        AiChatStreamRequest.ImageInput i2 = new AiChatStreamRequest.ImageInput();
        i2.setUrl("");
        req.setImages(List.of(i1, i2));

        service.streamChat(req, 1L, new MockHttpServletResponse());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(!merged.contains("image_url"));
    }

    @Test
    void streamChat_should_persist_usage_tokens_and_short_title_and_null_first_token_latency() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult(
                    "provider-x",
                    "model-x",
                    new LlmCallQueueService.UsageMetrics(6, 4, 10, null)
            );
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(5L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        session.setTitle(" ");
        when(qaSessionsRepository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(session));
        when(qaSessionsRepository.save(any(QaSessionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(81L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(82L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

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
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(5L);
        req.setMessage("short title");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: done"));

        ArgumentCaptor<QaMessagesEntity> msgCap = ArgumentCaptor.forClass(QaMessagesEntity.class);
        verify(qaMessagesRepository, atLeastOnce()).save(msgCap.capture());
        boolean hasAssistantWithTokens = msgCap.getAllValues().stream()
                .anyMatch(m -> m != null && m.getRole() == MessageRole.ASSISTANT && m.getTokensOut() != null);
        assertTrue(hasAssistantWithTokens);

        ArgumentCaptor<QaTurnsEntity> turnCap = ArgumentCaptor.forClass(QaTurnsEntity.class);
        verify(qaTurnsRepository, atLeastOnce()).save(turnCap.capture());
        QaTurnsEntity lastTurn = turnCap.getValue();
        assertEquals(null, lastTurn.getFirstTokenLatencyMs());

        ArgumentCaptor<QaSessionsEntity> sessionCap = ArgumentCaptor.forClass(QaSessionsEntity.class);
        verify(qaSessionsRepository).save(sessionCap.capture());
        assertEquals("short title", sessionCap.getValue().getTitle());
    }

    @Test
    void streamChat_should_cover_non_hybrid_empty_rag_hits_and_skip_context_prompt() throws Exception {
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
        ContextWindowsRepository contextWindowsRepository = mock(ContextWindowsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(6L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(6L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(91L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(92L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(201L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of());

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfg,
                new ChatRagAugmentConfigDTO(),
                null,
                null,
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                mock(RagContextPromptService.class),
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
        req.setSessionId(6L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);

        String body = resp.getContentAsString();
        assertTrue(body.contains("event: done"));
        verify(retrievalHitsRepository, never()).saveAll(any());
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));
    }

    @Test
    void streamChat_should_cover_context_log_sampling_false_when_sample_rate_nan() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ans [1]\"}");
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
        session.setId(7L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(101L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(102L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(202L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(1L);
        hit.setChunkIndex(1);
        hit.setScore(0.6);
        hit.setContentText("d");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.CitationSource s1 = new RagContextPromptService.CitationSource();
        s1.setIndex(1);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        assembled.setSources(List.of(s1));
        assembled.setSelected(null);
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        ContextClipConfigDTO contextCfg = new ContextClipConfigDTO();
        contextCfg.setLogEnabled(true);
        contextCfg.setLogSampleRate(Double.NaN);

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
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(7L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));
        verify(contextWindowsRepository, never()).save(any(ContextWindowsEntity.class));
    }

    @Test
    void streamChat_should_cover_fallback_provider_model_files_null_block_and_image_empty_list() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        when(fileAssetsRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(fileAssetExtractionsRepository.findByExtractStatusAndFileAssetIdGreaterThanOrderByFileAssetIdAsc(any(), anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(true, 20, "pid-default", "model-default");
        portalCfg.getAssistantChat().setTemperature(null);
        portalCfg.getAssistantChat().setTopP(0.8);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfg,
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
        req.setMessage("q");
        req.setUseRag(false);
        req.setDeepThink(true);
        req.setProviderId(" ");
        req.setModel(" ");
        req.setTemperature(null);
        req.setTopP(null);
        req.setImages(List.of());
        AiChatStreamRequest.FileInput f = new AiChatStreamRequest.FileInput();
        f.setFileAssetId(999L);
        req.setFiles(List.of(f));

        service.streamChat(req, 1L, new MockHttpServletResponse());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgCap = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> providerCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> modelCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> tempCap = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> topPCap = ArgumentCaptor.forClass(Double.class);
        verify(llmGateway).chatStreamRouted(any(), providerCap.capture(), modelCap.capture(), msgCap.capture(), tempCap.capture(), topPCap.capture(), any(), any(), any());
        assertEquals("pid-default", providerCap.getValue());
        assertEquals("model-default", modelCap.getValue());
        assertEquals(0.2, tempCap.getValue());
        assertEquals(0.8, topPCap.getValue());
        assertTrue(msgCap.getValue().toString().contains("q"));
    }

    @Test
    void streamChat_should_cover_routed_null_and_turn_null_and_tokens_null_paths() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return null;
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(8L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.NONE);
        session.setCreatedAt(LocalDateTime.now());
        session.setTitle("fixed");
        when(qaSessionsRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(111L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(112L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenReturn(null);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                qaSessionsRepository,
                qaMessagesRepository,
                qaTurnsRepository,
                mock(UsersRepository.class),
                portalCfgWithDefaults(false, 20, "pid-default", "model-default"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(8L);
        req.setMessage("m");
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: done"));
    }

    @Test
    void streamChat_should_cover_use_rag_true_dryrun_path_without_retrieval_persist() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"ok\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(3L);
        hit.setChunkIndex(1);
        hit.setScore(0.9);
        hit.setContentText("x");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        assembled.setSources(List.of());
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

        HybridRetrievalConfigDTO hybridCfg = new HybridRetrievalConfigDTO();
        hybridCfg.setEnabled(false);

        PortalChatConfigDTO portalCfg = portalCfgWithDefaults(false, 20, "pid", "m1");
        portalCfg.getAssistantChat().setDefaultUseRag(true);

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfg,
                new ChatRagAugmentConfigDTO(),
                hybridCfg,
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO(),
                ragContextPromptService,
                mock(HybridRagRetrievalService.class),
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setDryRun(true);
        req.setMessage("q");
        req.setUseRag(true);
        req.setDeepThink(false);
        service.streamChat(req, 1L, new MockHttpServletResponse());
        verify(retrievalEventsRepository, never()).save(any());
        verify(retrievalHitsRepository, never()).saveAll(any());
    }

    @Test
    void streamChat_should_cover_line_null_and_reasoning_when_think_already_open() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine(null);
            consumer.onLine("data: {\"reasoning_content\":\"r1\"}");
            consumer.onLine("data: {\"reasoning_content\":\"r2\"}");
            consumer.onLine("data: {\"content\":\"\"}");
            consumer.onLine("data: {\"content\":\"c\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        AiChatService service = buildServiceWithOverrides(
                llmGateway,
                mock(QaSessionsRepository.class),
                mock(QaMessagesRepository.class),
                mock(QaTurnsRepository.class),
                mock(UsersRepository.class),
                portalCfgWithDefaults(true, 20, "pid", "m1"),
                new ChatRagAugmentConfigDTO(),
                new HybridRetrievalConfigDTO(),
                new ContextClipConfigDTO(),
                citationCfgEnabled(),
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setDryRun(true);
        req.setMessage("q");
        req.setUseRag(false);
        req.setDeepThink(true);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("<think>r1"));
        assertTrue(body.contains("r2"));
        assertTrue(body.contains("</think>c"));
    }

    @Test
    void streamChat_should_cover_sources_without_matching_citations() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"no citation\"}");
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
        RetrievalEventsRepository retrievalEventsRepository = mock(RetrievalEventsRepository.class);
        RetrievalHitsRepository retrievalHitsRepository = mock(RetrievalHitsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(9L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        when(qaSessionsRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(121L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(122L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        RetrievalEventsEntity ev = new RetrievalEventsEntity();
        ev.setId(204L);
        when(retrievalEventsRepository.save(any(RetrievalEventsEntity.class))).thenReturn(ev);

        RagPostChatRetrievalService ragRetrievalService = mock(RagPostChatRetrievalService.class);
        RagPostChatRetrievalService.Hit hit = new RagPostChatRetrievalService.Hit();
        hit.setPostId(2L);
        hit.setChunkIndex(1);
        hit.setScore(0.7);
        hit.setContentText("d");
        when(ragRetrievalService.retrieve(any(), anyInt(), any())).thenReturn(List.of(hit));

        RagContextPromptService ragContextPromptService = mock(RagContextPromptService.class);
        RagContextPromptService.CitationSource s1 = new RagContextPromptService.CitationSource();
        s1.setIndex(1);
        s1.setSnippet("snip");
        RagContextPromptService.AssembleResult assembled = new RagContextPromptService.AssembleResult();
        assembled.setContextPrompt("ctx");
        assembled.setSources(List.of(s1));
        assembled.setSelected(List.of());
        assembled.setDropped(List.of());
        assembled.setChunkIds(Map.of());
        when(ragContextPromptService.assemble(any(), any(), any(), any())).thenReturn(assembled);

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
                mock(RagCommentChatRetrievalService.class),
                mock(RagChatPostCommentAggregationService.class),
                retrievalEventsRepository,
                retrievalHitsRepository,
                mock(ContextWindowsRepository.class),
                mock(QaMessageSourcesRepository.class),
                ragRetrievalService
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(9L);
        req.setMessage("q");
        req.setDryRun(false);
        req.setUseRag(true);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        String body = resp.getContentAsString();
        assertTrue(body.contains("event: done"));
        assertTrue(body.contains("event: sources"));
    }

    @Test
    void streamChat_should_cover_auto_title_condition_false_when_message_null() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: [DONE]");
            return new LlmGateway.RoutedChatStreamResult("p", "m", null);
        }).when(llmGateway).chatStreamRouted(any(), any(), any(), any(), any(), any(), any(), any(), any());

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);

        QaSessionsEntity session = new QaSessionsEntity();
        session.setId(10L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        session.setTitle(" ");
        when(qaSessionsRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(131L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(132L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaSessionsRepository.save(any(QaSessionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

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
                new ChatContextGovernanceConfigDTO()
        );

        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(10L);
        req.setMessage(null);
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        service.streamChat(req, 1L, resp);
        assertTrue(resp.getContentAsString().contains("event: done"));
        verify(qaSessionsRepository, never()).save(any(QaSessionsEntity.class));
    }

    @Test
    void streamChat_should_cover_auto_title_truncate_when_message_longer_than_60() throws Exception {
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
        session.setId(11L);
        session.setUserId(1L);
        session.setIsActive(true);
        session.setContextStrategy(ContextStrategy.RECENT_N);
        session.setCreatedAt(LocalDateTime.now());
        session.setTitle(" ");
        when(qaSessionsRepository.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(session));
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(qaMessagesRepository.save(any(QaMessagesEntity.class))).thenAnswer(inv -> {
            QaMessagesEntity e = inv.getArgument(0);
            if (e.getId() == null && e.getRole() == MessageRole.USER) e.setId(141L);
            if (e.getId() == null && e.getRole() == MessageRole.ASSISTANT) e.setId(142L);
            return e;
        });
        when(qaTurnsRepository.save(any(QaTurnsEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(qaSessionsRepository.save(any(QaSessionsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

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
                new ChatContextGovernanceConfigDTO()
        );

        String longText = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        AiChatStreamRequest req = new AiChatStreamRequest();
        req.setSessionId(11L);
        req.setMessage(longText);
        req.setDryRun(false);
        req.setUseRag(false);
        req.setDeepThink(false);
        service.streamChat(req, 1L, new MockHttpServletResponse());

        ArgumentCaptor<QaSessionsEntity> sessionCap = ArgumentCaptor.forClass(QaSessionsEntity.class);
        verify(qaSessionsRepository).save(sessionCap.capture());
        assertEquals(60, sessionCap.getValue().getTitle().length());
    }

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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        assertTrue(!dto.getContent().contains("fallback [1]"));
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
                any(org.springframework.data.jpa.domain.Specification.class),
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
        assertTrue(!merged.contains("after-q"));
        assertTrue(!merged.contains("later-q"));
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        assertTrue(!merged.contains("future"));
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
        assertTrue(!body.contains("</think></think>"));
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepositoryB.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        assertTrue(!merged.contains("skip-self"));
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
        assertTrue(!msgCap.getValue().toString().contains("Files:"));
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
        when(qaMessagesRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
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
                ragRetrievalService,
                mock(FileAssetsRepository.class),
                mock(FileAssetExtractionsRepository.class)
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
            RagPostChatRetrievalService ragRetrievalService,
            FileAssetsRepository fileAssetsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository
    ) {
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmModelEntity vision = new LlmModelEntity();
        vision.setEnabled(true);
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(any(), any(), any(), any())).thenReturn(Optional.of(vision));
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(any(), any())).thenReturn(List.of(vision));
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of());
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of());

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

    private static QaMessagesEntity messageWithRole(Long id, MessageRole role, String content, LocalDateTime createdAt) {
        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(id);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(createdAt);
        return msg;
    }
}
