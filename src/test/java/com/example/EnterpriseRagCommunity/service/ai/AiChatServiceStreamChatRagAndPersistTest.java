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


class AiChatServiceStreamChatRagAndPersistTest extends AiChatServiceStreamChatRagAndPersistTestSupport {
    @Test
    void streamChat_should_throw_when_current_user_id_is_null() {
        AiChatService service = buildService(mock(LlmGateway.class));
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> service.streamChat(new AiChatStreamRequest(), null, new MockHttpServletResponse()));
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(hist));

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

        ArgumentCaptor<QaMessagesEntity> userMsgCap = ArgumentCaptor.forClass(QaMessagesEntity.class);
        verify(qaMessagesRepository, atLeastOnce()).save(userMsgCap.capture());
        QaMessagesEntity savedUserMsg = userMsgCap.getAllValues().stream()
                .filter(m -> m != null && m.getRole() == MessageRole.USER)
                .findFirst()
                .orElse(userMsgCap.getValue());
        String savedText = savedUserMsg.getContent();
        assertTrue(savedText.contains("[IMAGES]"));
        assertTrue(savedText.contains("[FILES]"));

        ArgumentCaptor<List<ChatMessage>> cap = listCaptor();
        verify(llmGateway).chatStreamRouted(any(), any(), any(), cap.capture(), any(), any(), any(), any(), any());
        String merged = cap.getValue().toString();
        assertTrue(merged.contains("sys0"));
        assertTrue(merged.contains("a0"));
        assertTrue(merged.contains("user-sys"));
        assertFalse(merged.contains("skip"));
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
        assertFalse(body.contains("</think></think>"));
    }

    @Test
    void streamChat_should_normalize_quoted_citations_and_emit_sources_with_null_fields() throws Exception {
        LlmGateway llmGateway = mock(LlmGateway.class);
        doAnswer(inv -> {
            OpenAiCompatClient.SseLineConsumer consumer = inv.getArgument(8);
            consumer.onLine("data: {\"content\":\"\\\"src\\\"[1]\"}");
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        assertFalse(body.contains("event: sources"));
        assertFalse(body.contains("Sources"));
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
        assertFalse(merged.contains("image_url"));
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        assertNull(lastTurn.getFirstTokenLatencyMs());

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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
        when(qaMessagesRepository.findAll(anyQaMessageSpec(), any(org.springframework.data.domain.Pageable.class)))
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
}
