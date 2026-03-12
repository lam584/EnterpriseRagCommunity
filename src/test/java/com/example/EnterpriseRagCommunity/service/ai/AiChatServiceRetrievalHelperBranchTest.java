package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
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
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;

class AiChatServiceRetrievalHelperBranchTest {
    @Test
    void buildRagContextPrompt_should_apply_item_and_token_limits() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("buildRagContextPrompt", List.class, HybridRetrievalConfigDTO.class);
        m.setAccessible(true);

        RagPostChatRetrievalService.Hit h1 = new RagPostChatRetrievalService.Hit();
        h1.setPostId(1L);
        h1.setChunkIndex(1);
        h1.setScore(0.12);
        h1.setTitle("t1");
        h1.setContentText("abcdef");
        RagPostChatRetrievalService.Hit h2 = new RagPostChatRetrievalService.Hit();
        h2.setPostId(2L);
        h2.setChunkIndex(2);
        h2.setScore(0.34);
        h2.setTitle("t2");
        h2.setContentText("uvwxyz");

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setHybridK(2);
        cfg.setPerDocMaxTokens(3);
        cfg.setMaxInputTokens(3);
        String out = (String) m.invoke(null, List.of(h1, h2), cfg);
        assertTrue(out.contains("[1]"));
        assertNotNull(out);
    }

    @Test
    void toRagHits_should_use_fallback_score_and_post_ids() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("toRagHits", List.class);
        m.setAccessible(true);

        HybridRagRetrievalService.DocHit d = new HybridRagRetrievalService.DocHit();
        d.setDocId("d1");
        d.setPostId(null);
        d.setPostIds(List.of(9L));
        d.setScore(0.66);
        d.setRerankScore(null);
        d.setFusedScore(null);

        @SuppressWarnings("unchecked")
        List<RagPostChatRetrievalService.Hit> out = (List<RagPostChatRetrievalService.Hit>) m.invoke(null, List.of(d));
        assertEquals(1, out.size());
        assertEquals(9L, out.get(0).getPostId());
        assertEquals(0.66, out.get(0).getScore());
    }

    @Test
    void appendStageHits_should_select_stage_specific_scores() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("appendStageHits", List.class, Long.class, RetrievalHitType.class, List.class);
        m.setAccessible(true);
        List<RetrievalHitsEntity> out = new ArrayList<>();

        HybridRagRetrievalService.DocHit d = new HybridRagRetrievalService.DocHit();
        d.setPostIds(List.of(1L));
        d.setScore(null);
        d.setBm25Score(0.11);
        d.setVecScore(0.22);
        d.setRerankScore(0.33);

        m.invoke(null, out, 1L, RetrievalHitType.BM25, List.of(d));
        m.invoke(null, out, 1L, RetrievalHitType.VEC, List.of(d));
        m.invoke(null, out, 1L, RetrievalHitType.RERANK, List.of(d));
        assertEquals(3, out.size());
        assertEquals(0.11, out.get(0).getScore());
        assertEquals(0.22, out.get(1).getScore());
        assertEquals(0.33, out.get(2).getScore());
    }

    @Test
    void appendChatHits_and_appendCommentHits_should_default_null_score_to_zero() throws Exception {
        Method appendChat = AiChatService.class.getDeclaredMethod("appendChatHits", List.class, Long.class, RetrievalHitType.class, List.class);
        appendChat.setAccessible(true);
        Method appendComment = AiChatService.class.getDeclaredMethod("appendCommentHits", List.class, Long.class, RetrievalHitType.class, List.class);
        appendComment.setAccessible(true);

        List<RetrievalHitsEntity> out = new ArrayList<>();
        RagPostChatRetrievalService.Hit ch = new RagPostChatRetrievalService.Hit();
        ch.setPostId(7L);
        ch.setScore(null);
        RagCommentChatRetrievalService.Hit cm = new RagCommentChatRetrievalService.Hit();
        cm.setPostId(8L);
        cm.setCommentId(88L);
        cm.setScore(null);

        appendChat.invoke(null, out, 2L, RetrievalHitType.AGG, List.of(ch));
        appendComment.invoke(null, out, 2L, RetrievalHitType.COMMENT_VEC, List.of(cm));
        assertEquals(2, out.size());
        assertEquals(0.0, out.get(0).getScore());
        assertEquals(0.0, out.get(1).getScore());
        assertEquals(88L, out.get(1).getChunkId());
    }

    @Test
    void writeRagDebugEvent_should_skip_when_disabled_and_render_when_enabled() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod(
                "writeRagDebugEvent",
                PrintWriter.class,
                ChatRagAugmentConfigDTO.class,
                String.class,
                List.class,
                List.class,
                RagContextPromptService.AssembleResult.class
        );
        m.setAccessible(true);

        StringWriter sw1 = new StringWriter();
        ChatRagAugmentConfigDTO off = new ChatRagAugmentConfigDTO();
        off.setDebugEnabled(false);
        m.invoke(null, new PrintWriter(sw1), off, "q", List.of(), List.of(), null);
        assertEquals("", sw1.toString());

        StringWriter sw2 = new StringWriter();
        ChatRagAugmentConfigDTO on = new ChatRagAugmentConfigDTO();
        on.setDebugEnabled(true);
        on.setDebugMaxChars(20);

        RagPostChatRetrievalService.Hit agg = new RagPostChatRetrievalService.Hit();
        agg.setPostId(1L);
        agg.setContentText("preview-text-123456");
        RagCommentChatRetrievalService.Hit cm = new RagCommentChatRetrievalService.Hit();
        cm.setCommentId(9L);
        cm.setPostId(1L);
        cm.setScore(0.3);

        RagContextPromptService.Item it = new RagContextPromptService.Item();
        it.setRank(1);
        it.setPostId(1L);
        it.setScore(0.9);
        RagContextPromptService.AssembleResult ar = new RagContextPromptService.AssembleResult();
        ar.setSelected(List.of(it));
        m.invoke(null, new PrintWriter(sw2), on, "q", List.of(agg), List.of(cm), ar);
        String out = sw2.toString();
        assertTrue(out.contains("event: rag_debug"));
        assertTrue(out.contains("\"commentId\":9"));
    }

    @Test
    void sanitizeHitPostIds_should_null_out_missing_posts() throws Exception {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        AiChatService service = buildService(postsRepository, promptsRepository);

        com.example.EnterpriseRagCommunity.entity.content.PostsEntity p = new com.example.EnterpriseRagCommunity.entity.content.PostsEntity();
        p.setId(1L);
        when(postsRepository.findAllById(any())).thenReturn(List.of(p));

        RetrievalHitsEntity h1 = new RetrievalHitsEntity();
        h1.setPostId(1L);
        RetrievalHitsEntity h2 = new RetrievalHitsEntity();
        h2.setPostId(2L);

        Method m = AiChatService.class.getDeclaredMethod("sanitizeHitPostIds", List.class);
        m.setAccessible(true);
        m.invoke(service, List.of(h1, h2));
        assertEquals(1L, h1.getPostId());
        assertEquals(null, h2.getPostId());
    }

    @Test
    void resolvePromptText_should_cover_blank_hit_and_miss_paths() throws Exception {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("SYS-X");
        when(promptsRepository.findByPromptCode("a")).thenReturn(Optional.of(p));
        when(promptsRepository.findByPromptCode("b")).thenReturn(Optional.empty());
        AiChatService service = buildService(postsRepository, promptsRepository);

        Method m = AiChatService.class.getDeclaredMethod("resolvePromptText", String.class);
        m.setAccessible(true);
        assertEquals("", (String) m.invoke(service, " "));
        assertEquals("SYS-X", (String) m.invoke(service, "a"));
        assertEquals("", (String) m.invoke(service, "b"));
    }

    @Test
    void toCitationSourceDtos_should_skip_null_and_cap_at_two_hundred() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("toCitationSourceDtos", List.class);
        m.setAccessible(true);

        List<RagContextPromptService.CitationSource> src = new ArrayList<>();
        src.add(null);
        for (int i = 1; i <= 220; i++) {
            RagContextPromptService.CitationSource s = new RagContextPromptService.CitationSource();
            s.setIndex(i);
            s.setPostId((long) i);
            src.add(s);
        }
        @SuppressWarnings("unchecked")
        List<com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO.AiCitationSourceDTO> out =
                (List<com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO.AiCitationSourceDTO>) m.invoke(null, src);
        assertEquals(199, out.size());
        assertEquals(1, out.get(0).getIndex());
    }

    private static AiChatService buildService(PostsRepository postsRepository, PromptsRepository promptsRepository) {
        LlmGateway llmGateway = mock(LlmGateway.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmModelEntity mm = new LlmModelEntity();
        mm.setEnabled(true);
        when(llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(any(), any()))
                .thenReturn(List.of(mm));
        when(llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(any(), any(), any(), any()))
                .thenReturn(Optional.of(mm));

        QaSessionsRepository qaSessionsRepository = mock(QaSessionsRepository.class);
        QaMessagesRepository qaMessagesRepository = mock(QaMessagesRepository.class);
        QaTurnsRepository qaTurnsRepository = mock(QaTurnsRepository.class);
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
        QaMessageSourcesRepository qaMessageSourcesRepository = mock(QaMessageSourcesRepository.class);
        OpenSearchTokenizeService openSearchTokenizeService = mock(OpenSearchTokenizeService.class);
        TokenCountService tokenCountService = new TokenCountService(openSearchTokenizeService);
        UsersRepository usersRepository = mock(UsersRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        PortalChatConfigService portalChatConfigService = mock(PortalChatConfigService.class);
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO cfg = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO();
        com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.AssistantChatConfigDTO ac = new com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO.AssistantChatConfigDTO();
        ac.setSystemPromptCode("s");
        ac.setDeepThinkSystemPromptCode("s2");
        cfg.setAssistantChat(ac);
        when(portalChatConfigService.getConfigOrDefault()).thenReturn(cfg);
        ChatContextGovernanceConfigService chatContextGovernanceConfigService = mock(ChatContextGovernanceConfigService.class);
        when(chatContextGovernanceConfigService.getConfigOrDefault()).thenReturn(new ChatContextGovernanceConfigDTO());
        ChatContextGovernanceService chatContextGovernanceService = mock(ChatContextGovernanceService.class);
        when(chatContextGovernanceService.apply(any(), any(), any(), any())).thenAnswer(inv -> {
            ChatContextGovernanceService.ApplyResult r = new ChatContextGovernanceService.ApplyResult();
            r.setMessages(inv.getArgument(3));
            r.setChanged(false);
            r.setReason("ok");
            r.setBeforeTokens(0);
            r.setAfterTokens(0);
            r.setBeforeChars(0);
            r.setAfterChars(0);
            r.setDetail(Map.of());
            return r;
        });
        when(contextClipConfigService.getConfigOrDefault()).thenReturn(new ContextClipConfigDTO());
        when(citationConfigService.getConfigOrDefault()).thenReturn(new CitationConfigDTO());
        ChatRagAugmentConfigDTO c = new ChatRagAugmentConfigDTO();
        c.setEnabled(false);
        when(chatRagAugmentConfigService.getConfigOrDefault()).thenReturn(c);

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
