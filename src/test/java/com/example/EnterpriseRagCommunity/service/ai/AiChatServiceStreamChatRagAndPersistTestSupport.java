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


abstract class AiChatServiceStreamChatRagAndPersistTestSupport {
    protected static List<RagContextPromptService.CitationSource> buildSources(int n) {
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

    protected static DocHit docHit(Long postId, double score) {
        DocHit h = new DocHit();
        h.setPostId(postId);
        h.setScore(score);
        h.setBm25Score(score);
        h.setVecScore(score);
        h.setRerankScore(score);
        return h;
    }

    protected static CitationConfigDTO citationCfgEnabled() {
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

    protected static PortalChatConfigDTO portalCfgWithDefaults(boolean defaultDeepThink, Integer historyLimit, String providerId, String model) {
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

    protected static AiChatService buildService(LlmGateway llmGateway) {
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

    protected static AiChatService buildServiceWithOverrides(
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

    protected static AiChatService buildServiceWithOverrides(
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

    protected static AiChatService buildServiceWithOverrides(
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

    protected static AiChatService buildServiceWithOverrides(
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

    protected static QaMessagesEntity messageWithRole(Long id, MessageRole role, String content, LocalDateTime createdAt) {
        QaMessagesEntity msg = new QaMessagesEntity();
        msg.setId(id);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(createdAt);
        return msg;
    }

    protected static org.springframework.data.jpa.domain.Specification<QaMessagesEntity> anyQaMessageSpec() {
        return any();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected static <T> ArgumentCaptor<List<T>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
