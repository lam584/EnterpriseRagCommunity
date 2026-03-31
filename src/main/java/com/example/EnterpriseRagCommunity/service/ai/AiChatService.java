package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PortalChatConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ChatRagAugmentConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagChatPostCommentAggregationService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ChatRagAugmentConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final Logger logger = LoggerFactory.getLogger(AiChatService.class);
    private static final String ENV_DEFAULT = "default";

    private final LlmGateway llmGateway;
    private final LlmModelRepository llmModelRepository;
    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;
    private final RagPostChatRetrievalService ragRetrievalService;
    private final RagCommentChatRetrievalService ragCommentChatRetrievalService;
    private final RagChatPostCommentAggregationService ragChatPostCommentAggregationService;
    private final HybridRetrievalConfigService hybridRetrievalConfigService;
    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final ContextClipConfigService contextClipConfigService;
    private final CitationConfigService citationConfigService;
    private final ChatRagAugmentConfigService chatRagAugmentConfigService;
    private final RagContextPromptService ragContextPromptService;
    private final RetrievalEventsRepository retrievalEventsRepository;
    private final RetrievalHitsRepository retrievalHitsRepository;
    private final ContextWindowsRepository contextWindowsRepository;
    private final PostsRepository postsRepository;
    private final QaMessageSourcesRepository qaMessageSourcesRepository;
    private final TokenCountService tokenCountService;
    private final UsersRepository usersRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final PortalChatConfigService portalChatConfigService;
    private final PromptsRepository promptsRepository;
    private final ChatContextGovernanceConfigService chatContextGovernanceConfigService;
    private final ChatContextGovernanceService chatContextGovernanceService;

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

    private static String normalizeCitationMode(CitationConfigDTO citationCfg) {
        return AiChatCitationSupport.normalizeCitationMode(citationCfg);
    }

    private static boolean shouldExposeCitationSources(CitationConfigDTO citationCfg) {
        return AiChatCitationSupport.shouldExposeCitationSources(citationCfg);
    }

    private static boolean shouldStripInlineCitations(CitationConfigDTO citationCfg) {
        return AiChatCitationSupport.shouldStripInlineCitations(citationCfg);
    }

    private static String enforceCitationModeAnswerBody(CitationConfigDTO citationCfg, String answerText) {
        return AiChatCitationSupport.enforceCitationModeAnswerBody(citationCfg, answerText);
    }

    /**
     * 当模型未按要求输出 [n] 时，兜底返回全部可用来源；仅对需要展示来源的模式生效。
     */
    private static List<RagContextPromptService.CitationSource> resolveSourcesForOutput(
            CitationConfigDTO citationCfg,
            List<RagContextPromptService.CitationSource> sources,
            String answerText
    ) {
        return AiChatCitationSupport.resolveSourcesForOutput(citationCfg, sources, answerText);
    }

    private static List<AiChatResponseDTO.AiCitationSourceDTO> toCitationSourceDtos(List<RagContextPromptService.CitationSource> sources) {
        return AiChatCitationSupport.toCitationSourceDtos(sources);
    }

    public AiChatResponseDTO regenerateOnce(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId) {
        if (currentUserId == null) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        if (questionMessageId == null) throw new IllegalArgumentException("questionMessageId is required");
        if (req == null) throw new IllegalArgumentException("req is required");
        return regenerateOnceInternal(questionMessageId, req, currentUserId);
    }

    private static String buildSourcesEventData(List<RagContextPromptService.CitationSource> sources) {
        return AiChatCitationSupport.buildSourcesEventData(sources);
    }

    private static void appendCommentHits(List<RetrievalHitsEntity> out, Long eventId, List<RagCommentChatRetrievalService.Hit> hits) {
        appendCommentHits(out, eventId, RetrievalHitType.COMMENT_VEC, hits);
    }

    private static void appendCommentHits(List<RetrievalHitsEntity> out,
                                          Long eventId,
                                          RetrievalHitType type,
                                          List<RagCommentChatRetrievalService.Hit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        RetrievalHitType effectiveType = type == null ? RetrievalHitType.COMMENT_VEC : type;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            RagCommentChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(effectiveType);
            rh.setPostId(h.getPostId());
            rh.setChunkId(null);
            rh.setScore(h.getScore() == null ? 0.0 : h.getScore());
            out.add(rh);
        }
    }

    private static String buildRagContextPrompt(List<RagPostChatRetrievalService.Hit> hits, HybridRetrievalConfigDTO cfg) {
        int maxItems = cfg == null || cfg.getHybridK() == null ? 10 : Math.max(1, cfg.getHybridK());
        int perDocMaxTokens = cfg == null || cfg.getPerDocMaxTokens() == null ? 300 : Math.max(1, cfg.getPerDocMaxTokens());
        int perDocMaxChars = Math.clamp(perDocMaxTokens * 16, 32, 12_000);

        StringBuilder sb = new StringBuilder();
        sb.append("参考资料：\n");
        if (hits == null || hits.isEmpty()) return sb.toString();

        int emitted = 0;
        for (int i = 0; i < hits.size() && emitted < maxItems; i++) {
            RagPostChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;

            String title = toNonBlank(h.getTitle());
            if (title == null) title = "文档#" + (i + 1);

            String content = toNonBlank(h.getContentText());
            if (content == null) content = "";
            if (content.length() > perDocMaxChars) content = content.substring(0, perDocMaxChars);

            sb.append('[').append(i + 1).append("] ").append(title).append('\n');
            sb.append(content).append("\n\n");
            emitted += 1;
        }
        return sb.toString().trim();
    }

    private static void writeRagDebugEvent(
            PrintWriter out,
            ChatRagAugmentConfigDTO cfg,
            String queryText,
            List<RagPostChatRetrievalService.Hit> aggHits,
            List<RagCommentChatRetrievalService.Hit> commentHits,
            RagContextPromptService.AssembleResult contextAssembled
    ) {
        AiChatRetrievalSupport.writeRagDebugEvent(out, cfg, queryText, aggHits, commentHits, contextAssembled);
    }

    public void streamRegenerate(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId, HttpServletResponse response)
            throws IOException {
        doStreamRegenerate(questionMessageId, req, currentUserId, response);
    }

    private void doStreamRegenerate(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId, HttpServletResponse response)
            throws IOException {
        if (currentUserId == null) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        if (questionMessageId == null) throw new IllegalArgumentException("questionMessageId is required");
        if (req == null) throw new IllegalArgumentException("req is required");
        doStreamRegenerateInternal(questionMessageId, req, currentUserId, response);
    }

    private static void sanitizeHitChunkIds(List<RetrievalHitsEntity> hits) {
        AiChatRetrievalSupport.sanitizeHitChunkIds(hits);
    }

    private static boolean hasItems(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean shouldLoadHistory(AiChatStreamRequest req, QaSessionsEntity session) {
        return !req.getDryRun() && session.getId() != null && session.getId() > 0 && session.getContextStrategy() != ContextStrategy.NONE;
    }

    private static boolean isHybridEnabled(HybridRetrievalConfigDTO cfg) {
        return cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
    }

    private static boolean isAugmentEnabled(ChatRagAugmentConfigDTO cfg) {
        return cfg == null || cfg.getEnabled() == null || cfg.getEnabled();
    }

    private static boolean isCommentsEnabled(ChatRagAugmentConfigDTO cfg) {
        return cfg == null || cfg.getCommentsEnabled() == null || cfg.getCommentsEnabled();
    }

    private static boolean shouldPersistRetrieval(AiChatStreamRequest req) {
        return !req.getDryRun();
    }

    private static boolean isRagDebugEnabled(ChatRagAugmentConfigDTO cfg) {
        return cfg != null && Boolean.TRUE.equals(cfg.getDebugEnabled());
    }

    private static boolean shouldWriteContextWindow(
            AiChatStreamRequest req,
            Long retrievalEventId,
            RagContextPromptService.AssembleResult contextAssembled,
            ContextClipConfigDTO contextCfg
    ) {
        return !req.getDryRun()
                && retrievalEventId != null
                && contextAssembled != null
                && contextCfg != null
                && Boolean.TRUE.equals(contextCfg.getLogEnabled());
    }

    private static boolean isBlankLine(String line) {
        return line == null || line.isBlank();
    }

    private static boolean hasReasoningDelta(String reasoning, boolean[] thinkClosed) {
        return reasoning != null && !reasoning.isEmpty() && !thinkClosed[0];
    }

    private static boolean hasContentDelta(String content) {
        return content != null && !content.isEmpty();
    }

    private static boolean shouldAppendThinkCloseTag(StringBuilder assistantAccum, String content) {
        return !assistantAccum.toString().trim().endsWith("</think>") && !content.trim().startsWith("</think>");
    }

    private static List<RagContextPromptService.CitationSource> filterSourcesByCitations(
            List<RagContextPromptService.CitationSource> sources,
            String answerText
    ) {
        return AiChatCitationSupport.filterSourcesByCitations(sources, answerText);
    }

    private static String stripInlineCitationMarkers(String text) {
        return AiChatCitationSupport.stripInlineCitationMarkers(text);
    }

    private static Set<Integer> extractCitationIndexes(String text, int maxIndex) {
        return AiChatCitationSupport.extractCitationIndexes(text, maxIndex);
    }

    public void streamChat(AiChatStreamRequest req, Long currentUserId, HttpServletResponse response) throws IOException {
        if (currentUserId == null) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }

        logger.info(
                "ai_chat_stream_start userId={} reqSessionId={} dryRun={} historyLimit={}",
                currentUserId,
                req.getSessionId(),
                req.getDryRun(),
                req.getHistoryLimit()
        );

        // 1) session
        QaSessionsEntity session = ensureSession(req.getSessionId(), currentUserId, req.getDryRun());
        logger.info("ai_chat_session_resolved userId={} sessionId={}", currentUserId, session.getId());

        // 2) setup SSE response (after session validation)
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter out = response.getWriter();

        List<AiChatStreamRequest.ImageInput> images = resolveImages(req);
        List<AiChatStreamRequest.FileInput> files = resolveFiles(req);
        String messageForHistory = req.getMessage();
        if (hasItems(images)) {
            messageForHistory = appendImagesAsText(messageForHistory, images);
        }
        if (hasItems(files)) {
            messageForHistory = appendFilesAsText(messageForHistory, files);
        }

        // 3) persist user msg + turn shell
        QaMessagesEntity userMsg = null;
        QaTurnsEntity turn = null;
        if (!req.getDryRun()) {
            try {
                userMsg = new QaMessagesEntity();
                userMsg.setSessionId(session.getId());
                userMsg.setRole(MessageRole.USER);
                userMsg.setContent(messageForHistory);
                userMsg.setCreatedAt(LocalDateTime.now());
                userMsg = qaMessagesRepository.save(userMsg);

                turn = new QaTurnsEntity();
                turn.setSessionId(session.getId());
                turn.setQuestionMessageId(userMsg.getId());
                turn.setAnswerMessageId(null);
                turn.setCreatedAt(LocalDateTime.now());
                turn = qaTurnsRepository.save(turn);
            } catch (Exception ex) {
                logger.error("ai_chat_persist_turn_failed userId={} sessionId={}", currentUserId, session.getId(), ex);
                out.write("event: error\n");
                out.write("data: {\"message\":\"" + jsonEscape("数据操作失败：" + ex.getMessage()) + "\"}\n\n");
                out.write("event: done\n");
                out.write("data: {}\n\n");
                out.flush();
                return;
            }
        }

        out.write("event: meta\n");
        out.write("data: {\"sessionId\":" + session.getId() + (userMsg != null ? ",\"userMessageId\":" + userMsg.getId() : "") + "}\n\n");
        out.flush();

        // 4) load history for context
        PortalChatConfigDTO.AssistantChatConfigDTO portalCfg = portalChatConfigService.getConfigOrDefault().getAssistantChat();
        List<ChatMessage> messages = new ArrayList<>();
        boolean deepThink = req.getDeepThink() != null ? req.getDeepThink() : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());

        String systemPromptCode = deepThink ? portalCfg.getDeepThinkSystemPromptCode() : portalCfg.getSystemPromptCode();
        String systemPrompt = resolvePromptText(systemPromptCode);

        messages.add(ChatMessage.system(systemPrompt));
        String userSystemPrompt = loadUserDefaultSystemPrompt(currentUserId);
        if (userSystemPrompt != null) {
            messages.add(ChatMessage.system(userSystemPrompt));
        }

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0)
                ? req.getHistoryLimit()
                : (portalCfg.getHistoryLimit() != null && portalCfg.getHistoryLimit() > 0 ? portalCfg.getHistoryLimit() : 20);
        if (shouldLoadHistory(req, session)) {
            // fetch last N messages (excluding current user message which is not yet in context)
            var page = qaMessagesRepository.findAll(
                    (root, _query, cb) -> cb.equal(root.get("sessionId"), session.getId()),
                    PageRequest.of(0, historyLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            List<QaMessagesEntity> histDesc = new ArrayList<>(page.getContent());
            Collections.reverse(histDesc); // to asc
            for (QaMessagesEntity m : histDesc) {
                if (userMsg != null && Objects.equals(m.getId(), userMsg.getId())) continue;
                String role = switch (m.getRole()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                };
                messages.add(new ChatMessage(role, m.getContent()));
            }
        }

        boolean useRag = req.getUseRag() != null ? req.getUseRag() : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.clamp(ragTopKOverride, 1, 50);

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
        Long retrievalEventId = null;
        HybridRetrievalConfigDTO hybridCfg = null;
        HybridRagRetrievalService.RetrieveResult hybridResult = null;
        ContextClipConfigDTO contextCfg = null;
        CitationConfigDTO citationCfg = null;
        ChatRagAugmentConfigDTO chatRagCfg = null;
        RagContextPromptService.AssembleResult contextAssembled = null;
        List<RagContextPromptService.CitationSource> citedSourcesForPersist = null;
        try {
            contextCfg = contextClipConfigService.getConfigOrDefault();
            citationCfg = citationConfigService.getConfigOrDefault();
            chatRagCfg = chatRagAugmentConfigService.getConfigOrDefault();

            if (useRag) {
                hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
                List<RagPostChatRetrievalService.Hit> postHits;
                List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
                if (isHybridEnabled(hybridCfg)) {
                    if (safeRagTopKOverride > 0) {
                        HybridRetrievalConfigDTO copy = new HybridRetrievalConfigDTO();
                        org.springframework.beans.BeanUtils.copyProperties(hybridCfg, copy);
                        copy.setHybridK(safeRagTopKOverride);
                        hybridCfg = copy;
                    }
                    hybridResult = hybridRagRetrievalService.retrieve(req.getMessage(), null, hybridCfg, false);
                    postHits = toRagHits(hybridResult == null ? null : hybridResult.getFinalHits());
                } else {
                    int k = safeRagTopKOverride > 0
                            ? safeRagTopKOverride
                            : contextCfg == null || contextCfg.getMaxItems() == null ? 6 : Math.max(1, contextCfg.getMaxItems());
                    postHits = ragRetrievalService.retrieve(req.getMessage(), Math.min(50, k), null);
                }

                boolean augmentEnabled = isAugmentEnabled(chatRagCfg);
                if (augmentEnabled) {
                    boolean commentsEnabled = isCommentsEnabled(chatRagCfg);
                    if (commentsEnabled) {
                        int ck = chatRagCfg == null || chatRagCfg.getCommentTopK() == null ? 20 : Math.max(1, chatRagCfg.getCommentTopK());
                        commentHits = ragCommentChatRetrievalService.retrieve(req.getMessage(), ck);
                    }
                    RagChatPostCommentAggregationService.Config ac = new RagChatPostCommentAggregationService.Config();
                    ac.setMaxPosts(chatRagCfg == null ? null : chatRagCfg.getMaxPosts());
                    ac.setPerPostMaxCommentChunks(chatRagCfg == null ? null : chatRagCfg.getPerPostMaxCommentChunks());
                    ac.setPostContentMaxTokens(chatRagCfg == null ? null : chatRagCfg.getPostContentMaxTokens());
                    ac.setCommentChunkMaxTokens(chatRagCfg == null ? null : chatRagCfg.getCommentChunkMaxTokens());
                    RagChatPostCommentAggregationService.IncludePostContentPolicy pol = null;
                    String polRaw = chatRagCfg == null ? null : chatRagCfg.getIncludePostContentPolicy();
                    if (polRaw != null && !polRaw.isBlank()) {
                        try {
                            pol = RagChatPostCommentAggregationService.IncludePostContentPolicy.valueOf(polRaw.trim().toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                        }
                    }
                    ac.setIncludePostContentPolicy(pol);
                    ragHits = ragChatPostCommentAggregationService.aggregate(req.getMessage(), postHits, commentHits, ac);
                } else {
                    ragHits = postHits;
                }
                if (shouldPersistRetrieval(req)) {
                    RetrievalEventsEntity ev = new RetrievalEventsEntity();
                    ev.setUserId(currentUserId);
                    ev.setQueryText(req.getMessage());
                    if (isHybridEnabled(hybridCfg)) {
                        ev.setBm25K(hybridCfg.getBm25K());
                        ev.setVecK(hybridCfg.getVecK());
                        ev.setHybridK(hybridCfg.getHybridK());
                        ev.setRerankModel(Boolean.TRUE.equals(hybridCfg.getRerankEnabled()) ? hybridCfg.getRerankModel() : null);
                        ev.setRerankK(Boolean.TRUE.equals(hybridCfg.getRerankEnabled()) ? hybridCfg.getRerankK() : null);
                    } else {
                        ev.setBm25K(0);
                        ev.setVecK(6);
                        ev.setHybridK(null);
                        ev.setRerankModel(null);
                        ev.setRerankK(null);
                    }
                    ev.setCreatedAt(LocalDateTime.now());
                    ev = retrievalEventsRepository.save(ev);
                    retrievalEventId = ev.getId();

                    if (retrievalEventId != null) {
                        List<RetrievalHitsEntity> outHits = new ArrayList<>();
                        if (isHybridEnabled(hybridCfg)) {
                            appendStageHits(outHits, retrievalEventId, RetrievalHitType.BM25, hybridResult == null ? null : hybridResult.getBm25Hits());
                            appendStageHits(outHits, retrievalEventId, RetrievalHitType.VEC, hybridResult == null ? null : hybridResult.getVecHits());
                            appendStageHits(outHits, retrievalEventId, RetrievalHitType.RERANK, hybridResult == null ? null : hybridResult.getFinalHits());
                            appendCommentHits(outHits, retrievalEventId, commentHits);
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                        } else if (hasItems(ragHits)) {
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.VEC, postHits);
                            appendCommentHits(outHits, retrievalEventId, commentHits);
                            if (augmentEnabled) {
                                appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                            }
                        }
                        if (!outHits.isEmpty()) {
                            saveRetrievalHitsSafely(retrievalEventId, outHits);
                        }
                    }
                }

                if (hasItems(ragHits)) {
                    contextAssembled = ragContextPromptService.assemble(req.getMessage(), ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (hasText(prompt)) {
                        messages.add(ChatMessage.system(prompt));
                    }
                    if (isRagDebugEnabled(chatRagCfg)) {
                        writeRagDebugEvent(out, chatRagCfg, req.getMessage(), ragHits, commentHits, contextAssembled);
                    }
                    if (shouldWriteContextWindow(req, retrievalEventId, contextAssembled, contextCfg)) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.clamp(p, 0.0, 1.0)) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setBudgetTokens(contextAssembled.getBudgetTokens());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
                            cw.setSelectedItems(contextAssembled.getSelected() == null ? 0 : contextAssembled.getSelected().size());
                            cw.setDroppedItems(contextAssembled.getDropped() == null ? 0 : contextAssembled.getDropped().size());
                            cw.setChunkIds(contextAssembled.getChunkIds());
                            cw.setCreatedAt(LocalDateTime.now());
                            contextWindowsRepository.save(cw);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("ai_chat_rag_retrieval_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        String providerIdOverride = StringUtils.hasText(req.getProviderId()) ? req.getProviderId().trim() : portalCfg.getProviderId();
        String modelOverride = StringUtils.hasText(req.getModel()) ? req.getModel().trim() : portalCfg.getModel();

        String userMessageForModel = applyThinkingDirective(
                req.getMessage(),
                deepThink,
                resolveModelNameForThinkDirective(providerIdOverride, modelOverride)
        );
        ChatContextGovernanceConfigDTO govCfg = chatContextGovernanceConfigService.getConfigOrDefault();
        if (hasItems(files)) {
            String filesBlock = buildFilesBlockForModel(files, currentUserId, govCfg);
            if (hasText(filesBlock)) {
                userMessageForModel = userMessageForModel + "\n\n" + filesBlock.trim();
            }
        }
        boolean hasImages = hasItems(images);
        List<ChatMessage> messagesMultimodal = new ArrayList<>(messages);
        if (hasImages) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", userMessageForModel));
            for (AiChatStreamRequest.ImageInput img : images) {
                String url = encodeImageUrlForUpstream(img);
                if (url == null || url.isBlank()) continue;
                parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
            }
            messagesMultimodal.add(ChatMessage.userParts(parts));
        } else {
            messagesMultimodal.add(ChatMessage.user(userMessageForModel));
        }

        messages = messagesMultimodal;
        messages = chatContextGovernanceService.apply(
                currentUserId,
                session.getId(),
                userMsg == null ? null : userMsg.getId(),
                messages
        ).getMessages();

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAtMs = new long[] {0L};
        boolean[] thinkOpen = new boolean[] {false};
        boolean[] thinkClosed = new boolean[] {false};
        String model = null;
        Double temperature = req.getTemperature() != null ? req.getTemperature() : portalCfg.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;
        Double topP = req.getTopP() != null ? req.getTopP() : portalCfg.getTopP();

        QaMessagesEntity assistantMsg = null;

        try {
            OpenAiCompatClient.SseLineConsumer handler = line -> {
                if (isBlankLine(line)) return;
                if (!line.startsWith("data:")) return;

                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    return;
                }

                String reasoning = deepThink ? extractDeltaReasoningContent(data) : null;
                String content = extractDeltaContent(data);

                StringBuilder deltaOut = new StringBuilder();
                if (hasReasoningDelta(reasoning, thinkClosed)) {
                    if (!thinkOpen[0]) {
                        thinkOpen[0] = true;
                        if (!reasoning.trim().startsWith("<think>")) {
                            deltaOut.append("<think>");
                        }
                    }
                    deltaOut.append(reasoning);
                }
                if (hasContentDelta(content)) {
                    if (thinkOpen[0] && !thinkClosed[0]) {
                        thinkClosed[0] = true;
                        if (shouldAppendThinkCloseTag(assistantAccum, content)) {
                            deltaOut.append("</think>");
                        }
                    }
                    deltaOut.append(content);
                }
                if (deltaOut.isEmpty()) return;
                String delta = deltaOut.toString();

                if (firstDeltaAtMs[0] == 0L) firstDeltaAtMs[0] = System.currentTimeMillis();
                assistantAccum.append(delta);

                out.write("event: delta\n");
                out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                out.flush();
            };

            LlmGateway.RoutedChatStreamResult routed;
            LlmQueueTaskType chatTaskType = LlmQueueTaskType.MULTIMODAL_CHAT;
            ensureMultimodalModelForRequest(chatTaskType, providerIdOverride, modelOverride);
            routed = llmGateway.chatStreamRouted(
                    chatTaskType,
                    providerIdOverride,
                    modelOverride,
                    messages,
                    temperature,
                    topP,
                    deepThink,
                    null,
                    handler
            );
            model = routed == null ? null : routed.model();

            if (deepThink && thinkOpen[0] && !thinkClosed[0]) {
                thinkClosed[0] = true;
                String delta = "</think>";
                assistantAccum.append(delta);
                out.write("event: delta\n");
                out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                out.flush();
            }

            if (contextAssembled != null) {
                String normalized = normalizeCitationQuoteFormatting(assistantAccum.toString());
                String modeAdjusted = enforceCitationModeAnswerBody(citationCfg, normalized);
                if (!modeAdjusted.contentEquals(assistantAccum)) {
                    assistantAccum.setLength(0);
                    assistantAccum.append(modeAdjusted);
                }
                List<RagContextPromptService.CitationSource> citedSources = shouldExposeCitationSources(citationCfg)
                        ? resolveSourcesForOutput(citationCfg, contextAssembled.getSources(), assistantAccum.toString())
                        : List.of();
                citedSourcesForPersist = citedSources;

                String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, citedSources);
                if (hasText(sourcesText)) {
                    String delta = "\n\n" + sourcesText.trim();
                    assistantAccum.append(delta);
                    out.write("event: delta\n");
                    out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                    out.flush();
                }

                if (shouldExposeCitationSources(citationCfg) && !citedSources.isEmpty()) {
                    out.write("event: sources\n");
                    out.write("data: " + buildSourcesEventData(citedSources) + "\n\n");
                    out.flush();
                }
            }

            // finalize persistence
            if (!req.getDryRun()) {
                Integer userTokensIn = tokenCountService.countTextTokens(messageForHistory);
                if (userMsg != null && userTokensIn != null) {
                    userMsg.setTokensIn(userTokensIn);
                    qaMessagesRepository.save(userMsg);
                }

                assistantMsg = new QaMessagesEntity();
                assistantMsg.setSessionId(session.getId());
                assistantMsg.setRole(MessageRole.ASSISTANT);
                assistantMsg.setContent(assistantAccum.toString());
                assistantMsg.setModel(model);
                assistantMsg.setCreatedAt(LocalDateTime.now());
                assistantMsg = qaMessagesRepository.save(assistantMsg);

                if (turn != null) {
                    turn.setAnswerMessageId(assistantMsg.getId());
                    turn.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
                    Integer firstTokenLatencyMs = firstDeltaAtMs[0] > 0 ? (int) Math.max(0, firstDeltaAtMs[0] - startedAt) : null;
                    turn.setFirstTokenLatencyMs(firstTokenLatencyMs);
                    qaTurnsRepository.save(turn);
                }

                TokenCountService.TokenDecision decision = tokenCountService.decideChatTokens(
                        routed == null ? req.getProviderId() : routed.providerId(),
                        model,
                        deepThink,
                        routed == null ? null : routed.usage(),
                        messages,
                        assistantAccum.toString()
                );
                Integer tokensIn = decision == null ? null : decision.tokensIn();
                Integer tokensOut = decision == null ? null : decision.tokensOut();
                if (tokensIn != null || tokensOut != null) {
                    assistantMsg.setTokensIn(tokensIn);
                    assistantMsg.setTokensOut(tokensOut);
                    assistantMsg = qaMessagesRepository.save(assistantMsg);
                }

                if (contextAssembled != null && shouldExposeCitationSources(citationCfg)) {
                    persistAssistantSources(assistantMsg.getId(), citedSourcesForPersist);
                }

                // auto title if empty
                if ((session.getTitle() == null || session.getTitle().isBlank()) && req.getMessage() != null) {
                    String t = req.getMessage().trim();
                    if (t.length() > 60) t = t.substring(0, 60);
                    session.setTitle(t);
                    qaSessionsRepository.save(session);
                }
            }
        } catch (Exception ex) {
            logger.error("ai_chat_stream_failed userId={} sessionId={} model={}", currentUserId, session.getId(), model, ex);
            out.write("event: error\n");
            out.write("data: {\"message\":\"" + jsonEscape(String.valueOf(ex.getMessage())) + "\"}\n\n");
            out.flush();
        } finally {
            long latency = System.currentTimeMillis() - startedAt;
            logger.info("ai_chat_stream_done userId={} sessionId={} latencyMs={}", currentUserId, session.getId(), latency);
            out.write("event: done\n");
            out.write("data: {\"latencyMs\":" + latency + "}\n\n");
            out.flush();
        }
    }

    private static String normalizeCitationQuoteFormatting(String text) {
        return AiChatCitationSupport.normalizeCitationQuoteFormatting(text);
    }

    private static boolean isCitationQuote(char c) {
        return c == '"' || c == '“' || c == '”' || c == '「' || c == '」' || c == '『' || c == '』';
    }

    private static boolean isCitationOpenQuote(char c) {
        return c == '"' || c == '“' || c == '「' || c == '『';
    }

    private static int findCitationQuoteClose(String text, int start) {
        int n = text == null ? 0 : text.length();
        for (int i = Math.max(0, start); i < n; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') return -1;
            if (c == '\\' && i + 1 < n && isCitationQuote(text.charAt(i + 1))) {
                return i + 1;
            }
            if (c == '"' || c == '”' || c == '」' || c == '』') {
                return i;
            }
        }
        return -1;
    }

    private boolean providerSupportsVision(String providerId) {
        String pid = toNonBlank(providerId);
        if (pid == null) return false;
        try {
            AiProvidersConfigService.ResolvedProvider p = llmGateway.resolve(pid);
            Map<String, Object> metadata = p == null ? null : p.metadata();
            if (metadata == null || metadata.isEmpty()) return false;
            Object flag = metadata.get("supportsVision");
            if (flag instanceof Boolean b) return b;
            String s = toNonBlank(flag);
            return s != null && "true".equalsIgnoreCase(s);
        } catch (Exception ignored) {
            return false;
        }
    }

    public AiChatResponseDTO chatOnce(AiChatStreamRequest req, Long currentUserId) {
        if (currentUserId == null) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        if (req == null) throw new IllegalArgumentException("req is required");

        logger.info(
                "ai_chat_once_start userId={} reqSessionId={} dryRun={} historyLimit={}",
                currentUserId,
                req.getSessionId(),
                req.getDryRun(),
                req.getHistoryLimit()
        );

        QaSessionsEntity session = ensureSession(req.getSessionId(), currentUserId, req.getDryRun());
        logger.info("ai_chat_once_session_resolved userId={} sessionId={}", currentUserId, session.getId());

        List<AiChatStreamRequest.ImageInput> images = resolveImages(req);
        List<AiChatStreamRequest.FileInput> files = resolveFiles(req);
        String messageForHistory = req.getMessage();
        if (hasItems(images)) {
            messageForHistory = appendImagesAsText(messageForHistory, images);
        }
        if (hasItems(files)) {
            messageForHistory = appendFilesAsText(messageForHistory, files);
        }

        QaMessagesEntity userMsg = null;
        QaTurnsEntity turn = null;
        if (!req.getDryRun()) {
            userMsg = new QaMessagesEntity();
            userMsg.setSessionId(session.getId());
            userMsg.setRole(MessageRole.USER);
            userMsg.setContent(messageForHistory);
            userMsg.setCreatedAt(LocalDateTime.now());
            userMsg = qaMessagesRepository.save(userMsg);

            turn = new QaTurnsEntity();
            turn.setSessionId(session.getId());
            turn.setQuestionMessageId(userMsg.getId());
            turn.setAnswerMessageId(null);
            turn.setCreatedAt(LocalDateTime.now());
            turn = qaTurnsRepository.save(turn);
        }

        PortalChatConfigDTO.AssistantChatConfigDTO portalCfg = portalChatConfigService.getConfigOrDefault().getAssistantChat();
        List<ChatMessage> messages = new ArrayList<>();
        boolean deepThink = req.getDeepThink() != null ? req.getDeepThink() : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());

        String systemPromptCode = deepThink ? portalCfg.getDeepThinkSystemPromptCode() : portalCfg.getSystemPromptCode();
        String systemPrompt = resolvePromptText(systemPromptCode);

        messages.add(ChatMessage.system(systemPrompt));
        String userSystemPrompt = loadUserDefaultSystemPrompt(currentUserId);
        if (userSystemPrompt != null) {
            messages.add(ChatMessage.system(userSystemPrompt));
        }

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0)
                ? req.getHistoryLimit()
                : (portalCfg.getHistoryLimit() != null && portalCfg.getHistoryLimit() > 0 ? portalCfg.getHistoryLimit() : 20);
        if (!req.getDryRun() && session.getId() != null && session.getId() > 0 && session.getContextStrategy() != ContextStrategy.NONE) {
            var page = qaMessagesRepository.findAll(
                    (root, _query, cb) -> cb.equal(root.get("sessionId"), session.getId()),
                    PageRequest.of(0, historyLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            List<QaMessagesEntity> histDesc = new ArrayList<>(page.getContent());
            Collections.reverse(histDesc);
            for (QaMessagesEntity m : histDesc) {
                if (userMsg != null && Objects.equals(m.getId(), userMsg.getId())) continue;
                String role = switch (m.getRole()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                };
                messages.add(new ChatMessage(role, m.getContent()));
            }
        }

        boolean useRag = req.getUseRag() != null ? req.getUseRag() : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.clamp(ragTopKOverride, 1, 50);

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
        Long retrievalEventId = null;
        HybridRetrievalConfigDTO hybridCfg = null;
        HybridRagRetrievalService.RetrieveResult hybridResult = null;
        ContextClipConfigDTO contextCfg = null;
        CitationConfigDTO citationCfg = null;
        ChatRagAugmentConfigDTO chatRagCfg = null;
        RagContextPromptService.AssembleResult contextAssembled = null;
        try {
            contextCfg = contextClipConfigService.getConfigOrDefault();
            citationCfg = citationConfigService.getConfigOrDefault();
            chatRagCfg = chatRagAugmentConfigService.getConfigOrDefault();

            if (useRag) {
                hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
                List<RagPostChatRetrievalService.Hit> postHits;
                List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
                if (isHybridEnabled(hybridCfg)) {
                    if (safeRagTopKOverride > 0) {
                        HybridRetrievalConfigDTO copy = new HybridRetrievalConfigDTO();
                        org.springframework.beans.BeanUtils.copyProperties(hybridCfg, copy);
                        copy.setHybridK(safeRagTopKOverride);
                        hybridCfg = copy;
                    }
                    hybridResult = hybridRagRetrievalService.retrieve(req.getMessage(), null, hybridCfg, false);
                    postHits = toRagHits(hybridResult == null ? null : hybridResult.getFinalHits());
                } else {
                    int k = safeRagTopKOverride > 0
                            ? safeRagTopKOverride
                            : contextCfg == null || contextCfg.getMaxItems() == null ? 6 : Math.max(1, contextCfg.getMaxItems());
                    postHits = ragRetrievalService.retrieve(req.getMessage(), Math.min(50, k), null);
                }

                boolean augmentEnabled = isAugmentEnabled(chatRagCfg);
                if (augmentEnabled) {
                    boolean commentsEnabled = isCommentsEnabled(chatRagCfg);
                    if (commentsEnabled) {
                        int ck = chatRagCfg == null || chatRagCfg.getCommentTopK() == null ? 20 : Math.max(1, chatRagCfg.getCommentTopK());
                        commentHits = ragCommentChatRetrievalService.retrieve(req.getMessage(), ck);
                    }
                    RagChatPostCommentAggregationService.Config ac = new RagChatPostCommentAggregationService.Config();
                    ac.setMaxPosts(chatRagCfg == null ? null : chatRagCfg.getMaxPosts());
                    ac.setPerPostMaxCommentChunks(chatRagCfg == null ? null : chatRagCfg.getPerPostMaxCommentChunks());
                    ac.setPostContentMaxTokens(chatRagCfg == null ? null : chatRagCfg.getPostContentMaxTokens());
                    ac.setCommentChunkMaxTokens(chatRagCfg == null ? null : chatRagCfg.getCommentChunkMaxTokens());
                    RagChatPostCommentAggregationService.IncludePostContentPolicy pol = null;
                    String polRaw = chatRagCfg == null ? null : chatRagCfg.getIncludePostContentPolicy();
                    if (polRaw != null && !polRaw.isBlank()) {
                        try {
                            pol = RagChatPostCommentAggregationService.IncludePostContentPolicy.valueOf(polRaw.trim().toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                        }
                    }
                    ac.setIncludePostContentPolicy(pol);
                    ragHits = ragChatPostCommentAggregationService.aggregate(req.getMessage(), postHits, commentHits, ac);
                } else {
                    ragHits = postHits;
                }
                if (!req.getDryRun()) {
                    RetrievalEventsEntity ev = new RetrievalEventsEntity();
                    ev.setQueryText(req.getMessage());
                    ev.setUserId(currentUserId);
                    ev.setSessionId(session.getId());
                    ev.setCreatedAt(LocalDateTime.now());
                    retrievalEventId = retrievalEventsRepository.save(ev).getId();
                }

                if (hasItems(ragHits)) {
                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null) {
                        int max = Math.min(200, ragHits.size());
                        List<RetrievalHitsEntity> hitEntities = new ArrayList<>();
                        for (int i = 0; i < max; i++) {
                            RagPostChatRetrievalService.Hit h = ragHits.get(i);
                            if (h == null) continue;
                            RetrievalHitsEntity he = new RetrievalHitsEntity();
                            he.setEventId(retrievalEventId);
                            he.setRank(i + 1);
                            he.setHitType(h.getType() == null ? RetrievalHitType.POST : h.getType());
                            he.setPostId(h.getPostId());
                            he.setChunkId(null);
                            he.setScore(h.getScore() == null ? 0.0 : h.getScore());
                            he.setCreatedAt(LocalDateTime.now());
                            hitEntities.add(he);
                        }
                        if (!hitEntities.isEmpty()) saveRetrievalHitsSafely(retrievalEventId, hitEntities);
                    }

                    contextAssembled = ragContextPromptService.assemble(req.getMessage(), ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (prompt != null && !prompt.isBlank()) {
                        messages.add(1, ChatMessage.system(prompt));
                    }

                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null && contextAssembled != null && contextCfg != null
                            && Boolean.TRUE.equals(contextCfg.getLogEnabled())) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.clamp(p, 0.0, 1.0)) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setBudgetTokens(contextAssembled.getBudgetTokens());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
                            cw.setSelectedItems(contextAssembled.getSelected() == null ? 0 : contextAssembled.getSelected().size());
                            cw.setDroppedItems(contextAssembled.getDropped() == null ? 0 : contextAssembled.getDropped().size());
                            cw.setChunkIds(contextAssembled.getChunkIds());
                            cw.setCreatedAt(LocalDateTime.now());
                            contextWindowsRepository.save(cw);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("ai_chat_once_rag_retrieval_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        String providerIdOverride = StringUtils.hasText(req.getProviderId()) ? req.getProviderId().trim() : portalCfg.getProviderId();
        String modelOverride = StringUtils.hasText(req.getModel()) ? req.getModel().trim() : portalCfg.getModel();

        String userMessageForModel = applyThinkingDirective(
                req.getMessage(),
                deepThink,
                resolveModelNameForThinkDirective(providerIdOverride, modelOverride)
        );
        ChatContextGovernanceConfigDTO govCfg = chatContextGovernanceConfigService.getConfigOrDefault();
        if (hasItems(files)) {
            String filesBlock = buildFilesBlockForModel(files, currentUserId, govCfg);
            if (hasText(filesBlock)) {
                userMessageForModel = userMessageForModel + "\n\n" + filesBlock.trim();
            }
        }
        boolean hasImages = hasItems(images);
        List<ChatMessage> messagesMultimodal = new ArrayList<>(messages);
        if (hasImages) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", userMessageForModel));
            for (AiChatStreamRequest.ImageInput img : images) {
                String url = encodeImageUrlForUpstream(img);
                if (url == null || url.isBlank()) continue;
                parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
            }
            messagesMultimodal.add(ChatMessage.userParts(parts));
        } else {
            messagesMultimodal.add(ChatMessage.user(userMessageForModel));
        }

        messages = messagesMultimodal;
        messages = chatContextGovernanceService.apply(
                currentUserId,
                session.getId(),
                userMsg == null ? null : userMsg.getId(),
                messages
        ).getMessages();

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAtMs = new long[]{0L};
        boolean[] thinkOpen = new boolean[]{false};
        boolean[] thinkClosed = new boolean[]{false};
        String model = null;
        Double temperature = req.getTemperature() != null ? req.getTemperature() : portalCfg.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;
        Double topP = req.getTopP() != null ? req.getTopP() : portalCfg.getTopP();

        QaMessagesEntity assistantMsg = null;
        List<RagContextPromptService.CitationSource> citedSourcesForDto = List.of();
        long latency;
        try {
            OpenAiCompatClient.SseLineConsumer handler = line -> {
                if (line == null || line.isBlank()) return;
                if (!line.startsWith("data:")) return;

                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    return;
                }

                String reasoning = deepThink ? extractDeltaReasoningContent(data) : null;
                String content = extractDeltaContent(data);

                StringBuilder deltaOut = new StringBuilder();
                if (reasoning != null && !reasoning.isEmpty() && !thinkClosed[0]) {
                    if (!thinkOpen[0]) {
                        thinkOpen[0] = true;
                        if (!reasoning.trim().startsWith("<think>")) {
                            deltaOut.append("<think>");
                        }
                    }
                    deltaOut.append(reasoning);
                }
                if (content != null && !content.isEmpty()) {
                    if (thinkOpen[0] && !thinkClosed[0]) {
                        thinkClosed[0] = true;
                        if (!assistantAccum.toString().trim().endsWith("</think>") && !content.trim().startsWith("</think>")) {
                            deltaOut.append("</think>");
                        }
                    }
                    deltaOut.append(content);
                }
                if (deltaOut.isEmpty()) return;
                String delta = deltaOut.toString();

                if (firstDeltaAtMs[0] == 0L) firstDeltaAtMs[0] = System.currentTimeMillis();
                assistantAccum.append(delta);
            };

            LlmGateway.RoutedChatStreamResult routed;
            LlmQueueTaskType chatTaskType = LlmQueueTaskType.MULTIMODAL_CHAT;
            ensureMultimodalModelForRequest(chatTaskType, providerIdOverride, modelOverride);
            routed = llmGateway.chatStreamRouted(
                    chatTaskType,
                    providerIdOverride,
                    modelOverride,
                    messages,
                    temperature,
                    topP,
                    deepThink,
                    null,
                    handler
            );
            model = routed == null ? null : routed.model();

            if (deepThink && thinkOpen[0] && !thinkClosed[0]) {
                thinkClosed[0] = true;
                assistantAccum.append("</think>");
            }

            if (contextAssembled != null) {
                String normalized = normalizeCitationQuoteFormatting(assistantAccum.toString());
                String modeAdjusted = enforceCitationModeAnswerBody(citationCfg, normalized);
                if (!modeAdjusted.contentEquals(assistantAccum)) {
                    assistantAccum.setLength(0);
                    assistantAccum.append(modeAdjusted);
                }
                citedSourcesForDto = shouldExposeCitationSources(citationCfg)
                        ? resolveSourcesForOutput(citationCfg, contextAssembled.getSources(), assistantAccum.toString())
                        : List.of();

                String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, citedSourcesForDto);
                if (sourcesText != null && !sourcesText.isBlank()) {
                    assistantAccum.append("\n\n").append(sourcesText.trim());
                }
            }

            if (!req.getDryRun()) {
                Integer userTokensIn = tokenCountService.countTextTokens(messageForHistory);
                if (userMsg != null && userTokensIn != null) {
                    userMsg.setTokensIn(userTokensIn);
                    qaMessagesRepository.save(userMsg);
                }

                assistantMsg = new QaMessagesEntity();
                assistantMsg.setSessionId(session.getId());
                assistantMsg.setRole(MessageRole.ASSISTANT);
                assistantMsg.setContent(assistantAccum.toString());
                assistantMsg.setModel(model);
                assistantMsg.setCreatedAt(LocalDateTime.now());
                assistantMsg = qaMessagesRepository.save(assistantMsg);

                if (turn != null) {
                    turn.setAnswerMessageId(assistantMsg.getId());
                    turn.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
                    Integer firstTokenLatencyMs = firstDeltaAtMs[0] > 0 ? (int) Math.max(0, firstDeltaAtMs[0] - startedAt) : null;
                    turn.setFirstTokenLatencyMs(firstTokenLatencyMs);
                    qaTurnsRepository.save(turn);
                }

                TokenCountService.TokenDecision decision = tokenCountService.decideChatTokens(
                        routed == null ? providerIdOverride : routed.providerId(),
                        model,
                        deepThink,
                        routed == null ? null : routed.usage(),
                        messages,
                        assistantAccum.toString()
                );
                Integer tokensIn = decision == null ? null : decision.tokensIn();
                Integer tokensOut = decision == null ? null : decision.tokensOut();
                if (tokensIn != null || tokensOut != null) {
                    assistantMsg.setTokensIn(tokensIn);
                    assistantMsg.setTokensOut(tokensOut);
                    assistantMsg = qaMessagesRepository.save(assistantMsg);
                }

                if (contextAssembled != null && shouldExposeCitationSources(citationCfg)) {
                    persistAssistantSources(assistantMsg.getId(), citedSourcesForDto);
                }

                if ((session.getTitle() == null || session.getTitle().isBlank()) && req.getMessage() != null) {
                    String t = req.getMessage().trim();
                    if (t.length() > 60) t = t.substring(0, 60);
                    session.setTitle(t);
                    qaSessionsRepository.save(session);
                }
            }
        } catch (Exception ex) {
            logger.error("ai_chat_once_failed userId={} sessionId={} model={}", currentUserId, session.getId(), model, ex);
            throw ex;
        } finally {
            latency = System.currentTimeMillis() - startedAt;
            logger.info("ai_chat_once_done userId={} sessionId={} latencyMs={}", currentUserId, session.getId(), latency);
        }

        AiChatResponseDTO dto = new AiChatResponseDTO();
        dto.setSessionId(session.getId());
        dto.setUserMessageId(userMsg == null ? null : userMsg.getId());
        dto.setAssistantMessageId(assistantMsg == null ? null : assistantMsg.getId());
        dto.setContent(assistantAccum.toString());
        dto.setSources(toCitationSourceDtos(citedSourcesForDto));
        dto.setLatencyMs(latency);
        return dto;
    }

    /**
     * Extremely small extractor to avoid adding JSON deps.
     * It looks for "\"content\":\"...\"" under choices[0].delta.
     * <p>
     * This is not a full JSON parser but works for common OpenAI-compatible SSE frames.
     */
    static String extractDeltaContent(String json) {
        return AiChatSseSupport.extractDeltaContent(json);
    }

    static String extractDeltaReasoningContent(String json) {
        return AiChatSseSupport.extractDeltaReasoningContent(json);
    }

    static String extractDeltaStringField(String json, String field) {
        return AiChatSseSupport.extractDeltaStringField(json, field);
    }

    private static String decodeEscapedContent(String text) {
        return AiChatSseSupport.decodeEscapedContent(text);
    }

    private static String applyThinkingDirective(String content, boolean deepThink, String modelName) {
        String text = content == null ? "" : content;
        if (!supportsThinkingDirectiveModel(modelName)) return text;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("/no_think") || lower.contains("/think")) return text;
        String directive = deepThink ? "/think" : "/no_think";
        if (text.endsWith("\n") || text.endsWith("\r")) return text + directive;
        return text + "\n" + directive;
    }

    private static boolean supportsThinkingDirectiveModel(String modelName) {
        String raw = modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return false;

        String base = raw;
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < base.length()) base = base.substring(slash + 1);
        int colon = base.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < base.length()) base = base.substring(colon + 1);

        if (raw.contains("thinking") || base.contains("thinking")) return false;
        if (base.startsWith("qwen3-") || raw.startsWith("qwen3-")) return true;
        return base.startsWith("qwen-plus-2025-04-28")
                || base.startsWith("qwen-turbo-2025-04-28")
                || raw.startsWith("qwen-plus-2025-04-28")
                || raw.startsWith("qwen-turbo-2025-04-28");
    }

    private static String toNonBlank(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static List<AiChatStreamRequest.ImageInput> resolveImages(AiChatStreamRequest req) {
        return AiChatInputSupport.resolveImages(req);
    }

    private static List<AiChatStreamRequest.FileInput> resolveFiles(AiChatStreamRequest req) {
        return AiChatInputSupport.resolveFiles(req);
    }

    private static List<AiChatStreamRequest.FileInput> extractFilesFromHistoryText(String text) {
        return AiChatInputSupport.extractFilesFromHistoryText(text);
    }

    private static boolean isLikelyImageUrl(String url) {
        return AiChatInputSupport.isLikelyImageUrl(url);
    }

    private static String appendImagesAsText(String userMsg, List<AiChatStreamRequest.ImageInput> images) {
        return AiChatInputSupport.appendImagesAsText(userMsg, images);
    }

    private static String appendFilesAsText(String userMsg, List<AiChatStreamRequest.FileInput> files) {
        return AiChatInputSupport.appendFilesAsText(userMsg, files);
    }

    static String jsonEscape(String s) {
        return AiChatJsonSupport.jsonEscape(s);
    }

    private static List<RagPostChatRetrievalService.Hit> toRagHits(List<HybridRagRetrievalService.DocHit> hits) {
        return AiChatRetrievalSupport.toRagHits(hits);
    }

    private static void appendStageHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<HybridRagRetrievalService.DocHit> hits) {
        AiChatRetrievalSupport.appendStageHits(out, eventId, type, hits);
    }

    private static void appendChatHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<RagPostChatRetrievalService.Hit> hits) {
        AiChatRetrievalSupport.appendChatHits(out, eventId, type, hits);
    }

    private QaSessionsEntity ensureSession(Long sessionId, Long currentUserId, boolean dryRun) {
        if (sessionId != null) {
            QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("session not found"));
            if (Boolean.FALSE.equals(s.getIsActive())) {
                throw new IllegalArgumentException("session inactive");
            }
            return s;
        }

        QaSessionsEntity s = new QaSessionsEntity();
        s.setUserId(currentUserId);
        s.setTitle(null);
        s.setContextStrategy(ContextStrategy.RECENT_N);
        s.setIsActive(true);
        s.setCreatedAt(LocalDateTime.now());

        if (dryRun) {
            s.setId(-System.currentTimeMillis());
            return s;
        }
        return qaSessionsRepository.save(s);
    }

    private String buildFilesBlockForModel(List<AiChatStreamRequest.FileInput> files, Long currentUserId, ChatContextGovernanceConfigDTO cfg) {
        if (files == null || files.isEmpty() || currentUserId == null) return null;
        int maxFiles = cfg == null || cfg.getMaxFiles() == null ? 10 : Math.clamp(cfg.getMaxFiles(), 0, 50);
        if (maxFiles <= 0) return null;
        int perFileMaxChars = cfg == null || cfg.getPerFileMaxChars() == null ? 6000 : Math.max(100, cfg.getPerFileMaxChars());
        int totalFilesMaxChars = cfg == null || cfg.getTotalFilesMaxChars() == null ? 24000 : Math.max(100, cfg.getTotalFilesMaxChars());

        List<Long> ids = new ArrayList<>();
        for (AiChatStreamRequest.FileInput f : files) {
            if (f == null) continue;
            if (f.getFileAssetId() == null) continue;
            ids.add(f.getFileAssetId());
            if (ids.size() >= maxFiles) break;
        }
        if (ids.isEmpty()) return null;

        Map<Long, com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity> assets = new java.util.HashMap<>();
        for (var a : fileAssetsRepository.findAllById(ids)) {
            if (a == null || a.getId() == null) continue;
            assets.put(a.getId(), a);
        }

        List<Long> allowedIds = new ArrayList<>();
        for (Long id : ids) {
            var a = assets.get(id);
            if (a == null) continue;
            Long ownerId = null;
            try {
                ownerId = a.getOwner() == null ? null : a.getOwner().getId();
            } catch (Exception ignored) {
                ownerId = null;
            }
            if (ownerId == null || !ownerId.equals(currentUserId)) continue;
            allowedIds.add(id);
        }
        if (allowedIds.isEmpty()) return null;

        Map<Long, com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity> exMap = new java.util.HashMap<>();
        for (var ex : fileAssetExtractionsRepository.findAllById(allowedIds)) {
            if (ex == null || ex.getFileAssetId() == null) continue;
            exMap.put(ex.getFileAssetId(), ex);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[FILES]\n");
        int used = 0;
        int take = 0;
        for (Long id : allowedIds) {
            if (take >= maxFiles) break;
            var a = assets.get(id);
            if (a == null) continue;
            var ex = exMap.get(id);
            String name = toNonBlank(a.getOriginalName());
            String mt = toNonBlank(a.getMimeType());
            String url = toNonBlank(a.getUrl());
            sb.append("- file_asset_id=").append(id);
            if (name != null) sb.append(" name=").append(name);
            if (mt != null) sb.append(" mime=").append(mt);
            if (url != null) sb.append(" url=").append(url);
            if (ex != null && ex.getExtractStatus() != null) sb.append(" extract=").append(ex.getExtractStatus().name());
            sb.append("\n");

            if (ex != null) {
                String text = ex.getExtractedText();
                if (text != null && !text.isBlank()) {
                    String t = text.trim();
                    if (t.length() > perFileMaxChars) t = t.substring(0, perFileMaxChars);
                    if (used + t.length() > totalFilesMaxChars) {
                        int remain = Math.max(0, totalFilesMaxChars - used);
                        if (remain > 0) {
                            sb.append(t, 0, Math.min(remain, t.length()));
                            sb.append("\n\n");
                            used += Math.min(remain, t.length());
                        }
                        break;
                    }
                    sb.append(t).append("\n\n");
                    used += t.length();
                } else if (ex.getErrorMessage() != null && !ex.getErrorMessage().isBlank()) {
                    String em = ex.getErrorMessage().trim();
                    if (em.length() > 300) em = em.substring(0, 300);
                    sb.append("（解析失败：").append(em).append("）\n\n");
                }
            }
            take += 1;
        }
        return sb.toString().trim();
    }

    private String resolveModelNameForThinkDirective(String providerId, String modelOverride) {
        String m = toNonBlank(modelOverride);
        if (m != null) return m;
        try {
            AiProvidersConfigService.ResolvedProvider p = llmGateway.resolve(providerId);
            m = toNonBlank(p == null ? null : p.defaultChatModel());
            if (m != null) return m;
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String loadUserDefaultSystemPrompt(Long userId) {
        if (userId == null) return null;
        UsersEntity u = usersRepository.findById(userId).orElse(null);
        if (u == null) return null;
        Map<String, Object> metadata = u.getMetadata();
        if (metadata == null) return null;
        Object prefs = metadata.get("preferences");
        if (!(prefs instanceof Map)) return null;
        Object assistant = ((Map<String, Object>) prefs).get("assistant");
        if (!(assistant instanceof Map)) return null;
        Object v = ((Map<String, Object>) assistant).get("defaultSystemPrompt");
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return StringUtils.hasText(s) ? s : null;
    }

    private void ensureMultimodalModelForRequest(LlmQueueTaskType taskType, String providerId, String modelOverride) {
        if (taskType != LlmQueueTaskType.MULTIMODAL_CHAT) return;

        String mo = toNonBlank(modelOverride);
        String pid = toNonBlank(providerId);

        if (mo != null) {
            String effectiveProviderId = pid;
            if (effectiveProviderId == null) {
                try {
                    var p = llmGateway.resolve(null);
                    effectiveProviderId = p == null ? null : toNonBlank(p.id());
                } catch (Exception ignored) {
                }
            }
            if (effectiveProviderId == null) {
                throw new IllegalArgumentException("未指定模型提供商(providerId)，无法发送多模态请求");
            }
            if (!isEnabledMultimodalChatModel(effectiveProviderId, mo)) {
                throw new IllegalArgumentException("当前选择的模型未加入多模态聊天模型池，请切换为“自动”或在管理端配置该模型");
            }
            return;
        }

        if (pid != null) {
            try {
                var p = llmGateway.resolve(pid);
                String effectiveProviderId = p == null ? null : toNonBlank(p.id());
                String effectiveModel = p == null ? null : toNonBlank(p.defaultChatModel());
                if (effectiveProviderId == null || effectiveModel == null) {
                    throw new IllegalArgumentException("未配置可用的默认模型，无法发送多模态请求");
                }
                if (!isEnabledMultimodalChatModel(effectiveProviderId, effectiveModel)) {
                    throw new IllegalArgumentException("当前选择的默认模型未加入多模态聊天模型池，请切换为“自动”或在管理端配置该模型");
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("模型提供商解析失败，无法发送多模态请求");
            }
            return;
        }

        if (llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT, "MULTIMODAL_CHAT").isEmpty()) {
            throw new IllegalArgumentException("未配置“多模态聊天(MULTIMODAL_CHAT)”模型池，请先在管理端配置场景模型");
        }
    }

    private boolean isEnabledMultimodalChatModel(String providerId, String modelName) {
        String pid = toNonBlank(providerId);
        String mn = toNonBlank(modelName);
        if (pid == null || mn == null) return false;
        return llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(ENV_DEFAULT, pid, "MULTIMODAL_CHAT", mn)
                .filter((e) -> !Boolean.FALSE.equals(e.getEnabled()))
                .isPresent();
    }

    private String encodeImageUrlForUpstream(AiChatStreamRequest.ImageInput img) {
        if (img == null) return null;
        String url = toNonBlank(img.getUrl());
        if (url == null) return null;

        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        byte[] bytes = readLocalUploadBytes(img.getFileAssetId(), url);
        if (bytes == null || bytes.length == 0) return url;
        if (bytes.length > 4_000_000) return url;

        String mimeType = toNonBlank(img.getMimeType());
        if (!StringUtils.hasText(mimeType) && img.getFileAssetId() != null) {
            var fa = fileAssetsRepository.findById(img.getFileAssetId()).orElse(null);
            mimeType = fa == null ? null : toNonBlank(fa.getMimeType());
        }
        if (!StringUtils.hasText(mimeType)) mimeType = "application/octet-stream";

        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] readLocalUploadBytes(Long fileAssetId, String url) {
        try {
            String prefix = urlPrefix == null ? "/uploads" : urlPrefix.trim();
            String u = toNonBlank(url);
            if (u != null && !prefix.isEmpty() && u.startsWith(prefix + "/")) {
                int q = u.indexOf('?');
                if (q >= 0) u = u.substring(0, q);
                String rel = u.substring(prefix.length());
                while (rel.startsWith("/")) rel = rel.substring(1);

                Path root = Paths.get(uploadRoot == null ? "uploads" : uploadRoot).toAbsolutePath().normalize();
                Path p = root.resolve(rel).normalize();
                if (p.startsWith(root) && Files.exists(p) && Files.isRegularFile(p)) {
                    return Files.readAllBytes(p);
                }
            }

            if (fileAssetId != null) {
                var fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
                if (fa != null && fa.getPath() != null && !fa.getPath().isBlank()) {
                    Path p = Paths.get(fa.getPath()).toAbsolutePath().normalize();
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return Files.readAllBytes(p);
                    }
                }
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private AiChatResponseDTO regenerateOnceInternal(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId) {
        if (currentUserId == null) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        if (questionMessageId == null) throw new IllegalArgumentException("questionMessageId is required");
        if (req == null) throw new IllegalArgumentException("req is required");

        QaMessagesEntity questionMsg = qaMessagesRepository.findById(questionMessageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));
        if (questionMsg.getRole() != MessageRole.USER) {
            throw new IllegalArgumentException("只能对用户问题消息进行重新生成");
        }

        QaSessionsEntity session = qaSessionsRepository.findByIdAndUserId(questionMsg.getSessionId(), currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (Boolean.FALSE.equals(session.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }

        QaTurnsEntity turn = qaTurnsRepository.findByQuestionMessageId(questionMessageId).orElse(null);
        if (!Boolean.TRUE.equals(req.getDryRun())) {
            if (turn == null) {
                turn = new QaTurnsEntity();
                turn.setSessionId(session.getId());
                turn.setQuestionMessageId(questionMsg.getId());
                turn.setAnswerMessageId(null);
                turn.setCreatedAt(LocalDateTime.now());
                turn = qaTurnsRepository.save(turn);
            } else if (turn.getAnswerMessageId() != null) {
                turn.setAnswerMessageId(null);
                qaTurnsRepository.save(turn);
            }
        }

        PortalChatConfigDTO.AssistantChatConfigDTO portalCfg = portalChatConfigService.getConfigOrDefault().getAssistantChat();
        List<ChatMessage> messages = new ArrayList<>();
        boolean deepThink = req.getDeepThink() != null ? req.getDeepThink() : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());

        String systemPromptCode = deepThink ? portalCfg.getDeepThinkSystemPromptCode() : portalCfg.getSystemPromptCode();
        String systemPrompt = resolvePromptText(systemPromptCode);

        messages.add(ChatMessage.system(systemPrompt));
        String userSystemPrompt = loadUserDefaultSystemPrompt(currentUserId);
        if (userSystemPrompt != null) {
            messages.add(ChatMessage.system(userSystemPrompt));
        }

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0)
                ? req.getHistoryLimit()
                : (portalCfg.getHistoryLimit() != null && portalCfg.getHistoryLimit() > 0 ? portalCfg.getHistoryLimit() : 20);
        if (session.getId() != null && session.getId() > 0 && session.getContextStrategy() != ContextStrategy.NONE) {
            List<QaMessagesEntity> all = qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            List<QaMessagesEntity> before = new ArrayList<>();
            for (QaMessagesEntity m : all) {
                if (m == null) continue;
                if (Objects.equals(m.getId(), questionMsg.getId())) break;
                if (questionMsg.getCreatedAt() != null && m.getCreatedAt() != null && m.getCreatedAt().isAfter(questionMsg.getCreatedAt())) break;
                before.add(m);
            }
            int from = Math.max(0, before.size() - historyLimit);
            for (int i = from; i < before.size(); i++) {
                QaMessagesEntity m = before.get(i);
                String role = switch (m.getRole()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                };
                messages.add(new ChatMessage(role, m.getContent()));
            }
        }

        String questionText = questionMsg.getContent() == null ? "" : questionMsg.getContent();
        String providerIdOverride = StringUtils.hasText(req.getProviderId()) ? req.getProviderId().trim() : portalCfg.getProviderId();
        String modelOverride = StringUtils.hasText(req.getModel()) ? req.getModel().trim() : portalCfg.getModel();
        String userMessageForModel = applyThinkingDirective(
                questionText,
                deepThink,
                resolveModelNameForThinkDirective(providerIdOverride, modelOverride)
        );
        ChatContextGovernanceConfigDTO govCfg = chatContextGovernanceConfigService.getConfigOrDefault();
        List<AiChatStreamRequest.FileInput> filesFromHistory = extractFilesFromHistoryText(questionText);
        if (hasItems(filesFromHistory)) {
            String filesBlock = buildFilesBlockForModel(filesFromHistory, currentUserId, govCfg);
            if (hasText(filesBlock)) {
                userMessageForModel = userMessageForModel + "\n\n" + filesBlock.trim();
            }
        }
        messages.add(ChatMessage.user(userMessageForModel));

        boolean useRag = req.getUseRag() != null ? req.getUseRag() : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.clamp(ragTopKOverride, 1, 50);

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
        Long retrievalEventId = null;
        HybridRetrievalConfigDTO hybridCfg = null;
        HybridRagRetrievalService.RetrieveResult hybridResult = null;
        ContextClipConfigDTO contextCfg = null;
        CitationConfigDTO citationCfg = null;
        ChatRagAugmentConfigDTO chatRagCfg = null;
        RagContextPromptService.AssembleResult contextAssembled = null;
        List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
        try {
            contextCfg = contextClipConfigService.getConfigOrDefault();
            citationCfg = citationConfigService.getConfigOrDefault();
            chatRagCfg = chatRagAugmentConfigService.getConfigOrDefault();

            if (useRag) {
                hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
                List<RagPostChatRetrievalService.Hit> postHits;
                if (isHybridEnabled(hybridCfg)) {
                    if (safeRagTopKOverride > 0) {
                        HybridRetrievalConfigDTO copy = new HybridRetrievalConfigDTO();
                        org.springframework.beans.BeanUtils.copyProperties(hybridCfg, copy);
                        copy.setHybridK(safeRagTopKOverride);
                        hybridCfg = copy;
                    }
                    hybridResult = hybridRagRetrievalService.retrieve(questionText, null, hybridCfg, false);
                    postHits = toRagHits(hybridResult == null ? null : hybridResult.getFinalHits());
                } else {
                    int k = safeRagTopKOverride > 0
                            ? safeRagTopKOverride
                            : contextCfg == null || contextCfg.getMaxItems() == null ? 6 : Math.max(1, contextCfg.getMaxItems());
                    postHits = ragRetrievalService.retrieve(questionText, Math.min(50, k), null);
                }

                boolean augmentEnabled = isAugmentEnabled(chatRagCfg);
                if (augmentEnabled) {
                    boolean commentsEnabled = isCommentsEnabled(chatRagCfg);
                    if (commentsEnabled) {
                        int ck = chatRagCfg == null || chatRagCfg.getCommentTopK() == null ? 20 : Math.max(1, chatRagCfg.getCommentTopK());
                        commentHits = ragCommentChatRetrievalService.retrieve(questionText, ck);
                    }
                    RagChatPostCommentAggregationService.Config ac = new RagChatPostCommentAggregationService.Config();
                    ac.setMaxPosts(chatRagCfg == null ? null : chatRagCfg.getMaxPosts());
                    ac.setPerPostMaxCommentChunks(chatRagCfg == null ? null : chatRagCfg.getPerPostMaxCommentChunks());
                    ac.setPostContentMaxTokens(chatRagCfg == null ? null : chatRagCfg.getPostContentMaxTokens());
                    ac.setCommentChunkMaxTokens(chatRagCfg == null ? null : chatRagCfg.getCommentChunkMaxTokens());
                    RagChatPostCommentAggregationService.IncludePostContentPolicy pol = null;
                    String polRaw = chatRagCfg == null ? null : chatRagCfg.getIncludePostContentPolicy();
                    if (polRaw != null && !polRaw.isBlank()) {
                        try {
                            pol = RagChatPostCommentAggregationService.IncludePostContentPolicy.valueOf(polRaw.trim().toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                        }
                    }
                    ac.setIncludePostContentPolicy(pol);
                    ragHits = ragChatPostCommentAggregationService.aggregate(questionText, postHits, commentHits, ac);
                } else {
                    ragHits = postHits;
                }

                if (!Boolean.TRUE.equals(req.getDryRun())) {
                    RetrievalEventsEntity ev = new RetrievalEventsEntity();
                    ev.setQueryText(questionText);
                    ev.setUserId(currentUserId);
                    ev.setSessionId(session.getId());
                    ev.setCreatedAt(LocalDateTime.now());
                    retrievalEventId = retrievalEventsRepository.save(ev).getId();
                }

                if (!ragHits.isEmpty()) {
                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null) {
                        int max = Math.min(200, ragHits.size());
                        List<RetrievalHitsEntity> hitEntities = new ArrayList<>();
                        for (int i = 0; i < max; i++) {
                            RagPostChatRetrievalService.Hit h = ragHits.get(i);
                            if (h == null) continue;
                            RetrievalHitsEntity he = new RetrievalHitsEntity();
                            he.setEventId(retrievalEventId);
                            he.setRank(i + 1);
                            he.setHitType(h.getType() == null ? RetrievalHitType.POST : h.getType());
                            he.setPostId(h.getPostId());
                            he.setChunkId(null);
                            he.setScore(h.getScore() == null ? 0.0 : h.getScore());
                            he.setCreatedAt(LocalDateTime.now());
                            hitEntities.add(he);
                        }
                        if (!hitEntities.isEmpty()) saveRetrievalHitsSafely(retrievalEventId, hitEntities);
                    }

                    contextAssembled = ragContextPromptService.assemble(questionText, ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (prompt != null && !prompt.isBlank()) {
                        messages.add(1, ChatMessage.system(prompt));
                    }

                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null && contextAssembled != null && contextCfg != null
                            && Boolean.TRUE.equals(contextCfg.getLogEnabled())) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.clamp(p, 0.0, 1.0)) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setBudgetTokens(contextAssembled.getBudgetTokens());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
                            cw.setSelectedItems(contextAssembled.getSelected() == null ? 0 : contextAssembled.getSelected().size());
                            cw.setDroppedItems(contextAssembled.getDropped() == null ? 0 : contextAssembled.getDropped().size());
                            cw.setChunkIds(contextAssembled.getChunkIds());
                            cw.setCreatedAt(LocalDateTime.now());
                            contextWindowsRepository.save(cw);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("ai_chat_regenerate_once_rag_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        messages = chatContextGovernanceService.apply(
                currentUserId,
                session.getId(),
                questionMsg == null ? null : questionMsg.getId(),
                messages
        ).getMessages();

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAtMs = new long[]{0L};
        boolean[] thinkOpen = new boolean[]{false};
        boolean[] thinkClosed = new boolean[]{false};
        String model = null;
        Double temperature = req.getTemperature() != null ? req.getTemperature() : portalCfg.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;
        Double topP = req.getTopP() != null ? req.getTopP() : portalCfg.getTopP();

        QaMessagesEntity assistantMsg = null;
        List<RagContextPromptService.CitationSource> citedSourcesForDto = List.of();
        long latency;
        try {
            LlmGateway.RoutedChatStreamResult routed = llmGateway.chatStreamRouted(
                    LlmQueueTaskType.MULTIMODAL_CHAT,
                    providerIdOverride,
                    modelOverride,
                    messages,
                    temperature,
                    topP,
                    deepThink,
                    null,
                    line -> {
                        if (line == null || line.isBlank()) return;
                        if (!line.startsWith("data:")) return;

                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            return;
                        }

                        String reasoning = deepThink ? extractDeltaReasoningContent(data) : null;
                        String content = extractDeltaContent(data);

                        StringBuilder deltaOut = new StringBuilder();
                        if (reasoning != null && !reasoning.isEmpty() && !thinkClosed[0]) {
                            if (!thinkOpen[0]) {
                                thinkOpen[0] = true;
                                if (!reasoning.trim().startsWith("<think>")) {
                                    deltaOut.append("<think>");
                                }
                            }
                            deltaOut.append(reasoning);
                        }
                        if (content != null && !content.isEmpty()) {
                            if (thinkOpen[0] && !thinkClosed[0]) {
                                thinkClosed[0] = true;
                                if (!assistantAccum.toString().trim().endsWith("</think>") && !content.trim().startsWith("</think>")) {
                                    deltaOut.append("</think>");
                                }
                            }
                            deltaOut.append(content);
                        }
                        if (deltaOut.isEmpty()) return;
                        String delta = deltaOut.toString();

                        if (firstDeltaAtMs[0] == 0L) firstDeltaAtMs[0] = System.currentTimeMillis();
                        assistantAccum.append(delta);
                    }
            );
            model = routed == null ? null : routed.model();

            if (deepThink && thinkOpen[0] && !thinkClosed[0]) {
                thinkClosed[0] = true;
                assistantAccum.append("</think>");
            }

            if (contextAssembled != null) {
                String normalized = normalizeCitationQuoteFormatting(assistantAccum.toString());
                String modeAdjusted = enforceCitationModeAnswerBody(citationCfg, normalized);
                if (!modeAdjusted.contentEquals(assistantAccum)) {
                    assistantAccum.setLength(0);
                    assistantAccum.append(modeAdjusted);
                }
                citedSourcesForDto = shouldExposeCitationSources(citationCfg)
                        ? resolveSourcesForOutput(citationCfg, contextAssembled.getSources(), assistantAccum.toString())
                        : List.of();

                String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, citedSourcesForDto);
                if (sourcesText != null && !sourcesText.isBlank()) {
                    assistantAccum.append("\n\n").append(sourcesText.trim());
                }
            }

            if (!Boolean.TRUE.equals(req.getDryRun())) {
                Integer userTokensIn = tokenCountService.countTextTokens(questionText);
                if (userTokensIn != null) {
                    questionMsg.setTokensIn(userTokensIn);
                    qaMessagesRepository.save(questionMsg);
                }

                assistantMsg = new QaMessagesEntity();
                assistantMsg.setSessionId(session.getId());
                assistantMsg.setRole(MessageRole.ASSISTANT);
                assistantMsg.setContent(assistantAccum.toString());
                assistantMsg.setModel(model);
                assistantMsg.setCreatedAt(LocalDateTime.now());
                assistantMsg = qaMessagesRepository.save(assistantMsg);

                if (turn != null) {
                    turn.setAnswerMessageId(assistantMsg.getId());
                    turn.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
                    Integer firstTokenLatencyMs = firstDeltaAtMs[0] > 0 ? (int) Math.max(0, firstDeltaAtMs[0] - startedAt) : null;
                    turn.setFirstTokenLatencyMs(firstTokenLatencyMs);
                    qaTurnsRepository.save(turn);
                }

                TokenCountService.TokenDecision decision = tokenCountService.decideChatTokens(
                        routed == null ? providerIdOverride : routed.providerId(),
                        model,
                        deepThink,
                        routed == null ? null : routed.usage(),
                        messages,
                        assistantAccum.toString()
                );
                Integer tokensIn = decision == null ? null : decision.tokensIn();
                Integer tokensOut = decision == null ? null : decision.tokensOut();
                if (tokensIn != null || tokensOut != null) {
                    assistantMsg.setTokensIn(tokensIn);
                    assistantMsg.setTokensOut(tokensOut);
                    assistantMsg = qaMessagesRepository.save(assistantMsg);
                }

                if (contextAssembled != null && shouldExposeCitationSources(citationCfg)) {
                    persistAssistantSources(assistantMsg.getId(), citedSourcesForDto);
                }
            }
        } catch (Exception ex) {
            logger.error("ai_chat_regenerate_once_failed userId={} sessionId={} model={}", currentUserId, session.getId(), model, ex);
            throw ex;
        } finally {
            latency = System.currentTimeMillis() - startedAt;
        }

        AiChatResponseDTO dto = new AiChatResponseDTO();
        dto.setSessionId(session.getId());
        dto.setQuestionMessageId(questionMsg.getId());
        dto.setAssistantMessageId(assistantMsg == null ? null : assistantMsg.getId());
        dto.setContent(assistantAccum.toString());
        dto.setSources(toCitationSourceDtos(citedSourcesForDto));
        dto.setLatencyMs(latency);
        return dto;
    }

    private void doStreamRegenerateInternal(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId, HttpServletResponse response)
            throws IOException {
        QaMessagesEntity questionMsg = qaMessagesRepository.findById(questionMessageId)
                .orElseThrow(() -> new ResourceNotFoundException("message not found"));
        if (questionMsg.getRole() != MessageRole.USER) {
            throw new IllegalArgumentException("只能对用户问题消息进行重新生成");
        }

        QaSessionsEntity session = qaSessionsRepository.findByIdAndUserId(questionMsg.getSessionId(), currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (Boolean.FALSE.equals(session.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }

        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter out = response.getWriter();

        QaTurnsEntity turn = qaTurnsRepository.findByQuestionMessageId(questionMessageId).orElse(null);
        if (!Boolean.TRUE.equals(req.getDryRun())) {
            if (turn == null) {
                turn = new QaTurnsEntity();
                turn.setSessionId(session.getId());
                turn.setQuestionMessageId(questionMsg.getId());
                turn.setAnswerMessageId(null);
                turn.setCreatedAt(LocalDateTime.now());
                turn = qaTurnsRepository.save(turn);
            } else if (turn.getAnswerMessageId() != null) {
                turn.setAnswerMessageId(null);
                qaTurnsRepository.save(turn);
            }
        }

        out.write("event: meta\n");
        out.write("data: {\"sessionId\":" + session.getId() + ",\"questionMessageId\":" + questionMsg.getId() + "}\n\n");
        out.flush();

        PortalChatConfigDTO.AssistantChatConfigDTO portalCfg = portalChatConfigService.getConfigOrDefault().getAssistantChat();
        List<ChatMessage> messages = new ArrayList<>();
        boolean deepThink = req.getDeepThink() != null ? req.getDeepThink() : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());

        String systemPromptCode = deepThink ? portalCfg.getDeepThinkSystemPromptCode() : portalCfg.getSystemPromptCode();
        String systemPrompt = resolvePromptText(systemPromptCode);

        messages.add(ChatMessage.system(systemPrompt));
        String userSystemPrompt = loadUserDefaultSystemPrompt(currentUserId);
        if (userSystemPrompt != null) {
            messages.add(ChatMessage.system(userSystemPrompt));
        }

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0)
                ? req.getHistoryLimit()
                : (portalCfg.getHistoryLimit() != null && portalCfg.getHistoryLimit() > 0 ? portalCfg.getHistoryLimit() : 20);
        if (session.getId() != null && session.getId() > 0 && session.getContextStrategy() != ContextStrategy.NONE) {
            List<QaMessagesEntity> all = qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            List<QaMessagesEntity> before = new ArrayList<>();
            for (QaMessagesEntity m : all) {
                if (m == null) continue;
                if (Objects.equals(m.getId(), questionMsg.getId())) break;
                if (questionMsg.getCreatedAt() != null && m.getCreatedAt() != null && m.getCreatedAt().isAfter(questionMsg.getCreatedAt())) break;
                before.add(m);
            }
            int from = Math.max(0, before.size() - historyLimit);
            for (int i = from; i < before.size(); i++) {
                QaMessagesEntity m = before.get(i);
                String role = switch (m.getRole()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                };
                messages.add(new ChatMessage(role, m.getContent()));
            }
        }

        String questionText = questionMsg.getContent();
        String providerIdOverride = StringUtils.hasText(req.getProviderId()) ? req.getProviderId().trim() : portalCfg.getProviderId();
        String modelOverride = StringUtils.hasText(req.getModel()) ? req.getModel().trim() : portalCfg.getModel();
        String questionTextForModel = applyThinkingDirective(
                questionText,
                deepThink,
                resolveModelNameForThinkDirective(providerIdOverride, modelOverride)
        );
        ChatContextGovernanceConfigDTO govCfg = chatContextGovernanceConfigService.getConfigOrDefault();
        List<AiChatStreamRequest.FileInput> filesFromHistory = extractFilesFromHistoryText(questionText);
        if (hasItems(filesFromHistory)) {
            String filesBlock = buildFilesBlockForModel(filesFromHistory, currentUserId, govCfg);
            if (hasText(filesBlock)) {
                questionTextForModel = questionTextForModel + "\n\n" + filesBlock.trim();
            }
        }
        messages.add(ChatMessage.user(questionTextForModel));

        boolean useRag = req.getUseRag() != null ? req.getUseRag() : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.clamp(ragTopKOverride, 1, 50);

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
        Long retrievalEventId = null;
        HybridRetrievalConfigDTO hybridCfg = null;
        HybridRagRetrievalService.RetrieveResult hybridResult = null;
        ContextClipConfigDTO contextCfg = null;
        CitationConfigDTO citationCfg = null;
        ChatRagAugmentConfigDTO chatRagCfg = null;
        RagContextPromptService.AssembleResult contextAssembled = null;
        try {
            contextCfg = contextClipConfigService.getConfigOrDefault();
            citationCfg = citationConfigService.getConfigOrDefault();
            chatRagCfg = chatRagAugmentConfigService.getConfigOrDefault();

            if (useRag) {
                hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
                List<RagPostChatRetrievalService.Hit> postHits;
                List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
                if (isHybridEnabled(hybridCfg)) {
                    if (safeRagTopKOverride > 0) {
                        HybridRetrievalConfigDTO copy = new HybridRetrievalConfigDTO();
                        org.springframework.beans.BeanUtils.copyProperties(hybridCfg, copy);
                        copy.setHybridK(safeRagTopKOverride);
                        hybridCfg = copy;
                    }
                    hybridResult = hybridRagRetrievalService.retrieve(questionText, null, hybridCfg, false);
                    postHits = toRagHits(hybridResult == null ? null : hybridResult.getFinalHits());
                } else {
                    int k = safeRagTopKOverride > 0
                            ? safeRagTopKOverride
                            : contextCfg == null || contextCfg.getMaxItems() == null ? 6 : Math.max(1, contextCfg.getMaxItems());
                    postHits = ragRetrievalService.retrieve(questionText, Math.min(50, k), null);
                }

                boolean augmentEnabled = isAugmentEnabled(chatRagCfg);
                if (augmentEnabled) {
                    boolean commentsEnabled = isCommentsEnabled(chatRagCfg);
                    if (commentsEnabled) {
                        int ck = chatRagCfg == null || chatRagCfg.getCommentTopK() == null ? 20 : Math.max(1, chatRagCfg.getCommentTopK());
                        commentHits = ragCommentChatRetrievalService.retrieve(questionText, ck);
                    }
                    RagChatPostCommentAggregationService.Config ac = new RagChatPostCommentAggregationService.Config();
                    ac.setMaxPosts(chatRagCfg == null ? null : chatRagCfg.getMaxPosts());
                    ac.setPerPostMaxCommentChunks(chatRagCfg == null ? null : chatRagCfg.getPerPostMaxCommentChunks());
                    ac.setPostContentMaxTokens(chatRagCfg == null ? null : chatRagCfg.getPostContentMaxTokens());
                    ac.setCommentChunkMaxTokens(chatRagCfg == null ? null : chatRagCfg.getCommentChunkMaxTokens());
                    RagChatPostCommentAggregationService.IncludePostContentPolicy pol = null;
                    String polRaw = chatRagCfg == null ? null : chatRagCfg.getIncludePostContentPolicy();
                    if (polRaw != null && !polRaw.isBlank()) {
                        try {
                            pol = RagChatPostCommentAggregationService.IncludePostContentPolicy.valueOf(polRaw.trim().toUpperCase(Locale.ROOT));
                        } catch (Exception ignored) {
                        }
                    }
                    ac.setIncludePostContentPolicy(pol);
                    ragHits = ragChatPostCommentAggregationService.aggregate(questionText, postHits, commentHits, ac);
                } else {
                    ragHits = postHits;
                }
                if (!Boolean.TRUE.equals(req.getDryRun())) {
                    RetrievalEventsEntity ev = new RetrievalEventsEntity();
                    ev.setUserId(currentUserId);
                    ev.setQueryText(questionText);
                    if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
                        ev.setBm25K(hybridCfg.getBm25K());
                        ev.setVecK(hybridCfg.getVecK());
                        ev.setHybridK(hybridCfg.getHybridK());
                        ev.setRerankModel(Boolean.TRUE.equals(hybridCfg.getRerankEnabled()) ? hybridCfg.getRerankModel() : null);
                        ev.setRerankK(Boolean.TRUE.equals(hybridCfg.getRerankEnabled()) ? hybridCfg.getRerankK() : null);
                    } else {
                        ev.setBm25K(0);
                        ev.setVecK(6);
                        ev.setHybridK(null);
                        ev.setRerankModel(null);
                        ev.setRerankK(null);
                    }
                    ev.setCreatedAt(LocalDateTime.now());
                    ev = retrievalEventsRepository.save(ev);
                    retrievalEventId = ev.getId();

                    if (retrievalEventId != null) {
                        List<RetrievalHitsEntity> outHits = new ArrayList<>();
                        if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
                            appendStageHits(outHits, retrievalEventId, RetrievalHitType.BM25, hybridResult == null ? null : hybridResult.getBm25Hits());
                            appendStageHits(outHits, retrievalEventId, RetrievalHitType.VEC, hybridResult == null ? null : hybridResult.getVecHits());
                            appendStageHits(outHits, retrievalEventId, RetrievalHitType.RERANK, hybridResult == null ? null : hybridResult.getFinalHits());
                            appendCommentHits(outHits, retrievalEventId, commentHits);
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                        } else if (ragHits != null && !ragHits.isEmpty()) {
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.VEC, postHits);
                            appendCommentHits(outHits, retrievalEventId, commentHits);
                            if (augmentEnabled) {
                                appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                            }
                        }
                        if (!outHits.isEmpty()) {
                            saveRetrievalHitsSafely(retrievalEventId, outHits);
                        }
                    }
                }

                if (ragHits != null && !ragHits.isEmpty()) {
                    contextAssembled = ragContextPromptService.assemble(questionText, ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (prompt != null && !prompt.isBlank()) {
                        messages.add(1, ChatMessage.system(prompt));
                    }
                    if (chatRagCfg != null && Boolean.TRUE.equals(chatRagCfg.getDebugEnabled())) {
                        writeRagDebugEvent(out, chatRagCfg, questionText, ragHits, commentHits, contextAssembled);
                    }
                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null && contextAssembled != null && contextCfg != null
                            && Boolean.TRUE.equals(contextCfg.getLogEnabled())) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.clamp(p, 0.0, 1.0)) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setBudgetTokens(contextAssembled.getBudgetTokens());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
                            cw.setSelectedItems(contextAssembled.getSelected() == null ? 0 : contextAssembled.getSelected().size());
                            cw.setDroppedItems(contextAssembled.getDropped() == null ? 0 : contextAssembled.getDropped().size());
                            cw.setChunkIds(contextAssembled.getChunkIds());
                            cw.setCreatedAt(LocalDateTime.now());
                            contextWindowsRepository.save(cw);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("ai_chat_regenerate_rag_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        messages = chatContextGovernanceService.apply(
                currentUserId,
                session.getId(),
                questionMsg == null ? null : questionMsg.getId(),
                messages
        ).getMessages();

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAtMs = new long[] {0L};
        boolean[] thinkOpen = new boolean[] {false};
        boolean[] thinkClosed = new boolean[] {false};
        String model = null;
        Double temperature = req.getTemperature() != null ? req.getTemperature() : portalCfg.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;
        Double topP = req.getTopP() != null ? req.getTopP() : portalCfg.getTopP();

        QaMessagesEntity assistantMsg = null;

        try {
            LlmGateway.RoutedChatStreamResult routed = llmGateway.chatStreamRouted(
                    LlmQueueTaskType.MULTIMODAL_CHAT,
                    providerIdOverride,
                    modelOverride,
                    messages,
                    temperature,
                    topP,
                    deepThink,
                    null,
                    line -> {
                        if (line == null || line.isBlank()) return;
                        if (!line.startsWith("data:")) return;

                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            return;
                        }

                        String reasoning = deepThink ? extractDeltaReasoningContent(data) : null;
                        String content = extractDeltaContent(data);

                        StringBuilder deltaOut = new StringBuilder();
                        if (reasoning != null && !reasoning.isEmpty() && !thinkClosed[0]) {
                            if (!thinkOpen[0]) {
                                thinkOpen[0] = true;
                                if (!reasoning.trim().startsWith("<think>")) {
                                    deltaOut.append("<think>");
                                }
                            }
                            deltaOut.append(reasoning);
                        }
                        if (content != null && !content.isEmpty()) {
                            if (thinkOpen[0] && !thinkClosed[0]) {
                                thinkClosed[0] = true;
                                if (!assistantAccum.toString().trim().endsWith("</think>") && !content.trim().startsWith("</think>")) {
                                    deltaOut.append("</think>");
                                }
                            }
                            deltaOut.append(content);
                        }
                        if (deltaOut.isEmpty()) return;
                        String delta = deltaOut.toString();

                        if (firstDeltaAtMs[0] == 0L) firstDeltaAtMs[0] = System.currentTimeMillis();
                        assistantAccum.append(delta);

                        out.write("event: delta\n");
                        out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                        out.flush();
                    }
            );
            model = routed == null ? null : routed.model();

            if (deepThink && thinkOpen[0] && !thinkClosed[0]) {
                thinkClosed[0] = true;
                String delta = "</think>";
                assistantAccum.append(delta);
                out.write("event: delta\n");
                out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                out.flush();
            }

            if (contextAssembled != null) {
                String normalized = normalizeCitationQuoteFormatting(assistantAccum.toString());
                String modeAdjusted = enforceCitationModeAnswerBody(citationCfg, normalized);
                if (!modeAdjusted.contentEquals(assistantAccum)) {
                    assistantAccum.setLength(0);
                    assistantAccum.append(modeAdjusted);
                }
                List<RagContextPromptService.CitationSource> citedSources = shouldExposeCitationSources(citationCfg)
                        ? resolveSourcesForOutput(citationCfg, contextAssembled.getSources(), assistantAccum.toString())
                        : List.of();

                String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, citedSources);
                if (sourcesText != null && !sourcesText.isBlank()) {
                    String delta = "\n\n" + sourcesText.trim();
                    assistantAccum.append(delta);
                    out.write("event: delta\n");
                    out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                    out.flush();
                }

                if (shouldExposeCitationSources(citationCfg) && !citedSources.isEmpty()) {
                    out.write("event: sources\n");
                    out.write("data: " + buildSourcesEventData(citedSources) + "\n\n");
                    out.flush();
                }
            }

            if (!Boolean.TRUE.equals(req.getDryRun())) {
                Integer userTokensIn = tokenCountService.countTextTokens(questionText);
                if (userTokensIn != null) {
                    questionMsg.setTokensIn(userTokensIn);
                    qaMessagesRepository.save(questionMsg);
                }

                assistantMsg = new QaMessagesEntity();
                assistantMsg.setSessionId(session.getId());
                assistantMsg.setRole(MessageRole.ASSISTANT);
                assistantMsg.setContent(assistantAccum.toString());
                assistantMsg.setModel(model);
                assistantMsg.setCreatedAt(LocalDateTime.now());
                assistantMsg = qaMessagesRepository.save(assistantMsg);

                if (turn != null) {
                    turn.setAnswerMessageId(assistantMsg.getId());
                    turn.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
                    Integer firstTokenLatencyMs = firstDeltaAtMs[0] > 0 ? (int) Math.max(0, firstDeltaAtMs[0] - startedAt) : null;
                    turn.setFirstTokenLatencyMs(firstTokenLatencyMs);
                    qaTurnsRepository.save(turn);
                }

                TokenCountService.TokenDecision decision = tokenCountService.decideChatTokens(
                        routed == null ? providerIdOverride : routed.providerId(),
                        model,
                        deepThink,
                        routed == null ? null : routed.usage(),
                        messages,
                        assistantAccum.toString()
                );
                Integer tokensIn = decision == null ? null : decision.tokensIn();
                Integer tokensOut = decision == null ? null : decision.tokensOut();
                if (tokensIn != null || tokensOut != null) {
                    assistantMsg.setTokensIn(tokensIn);
                    assistantMsg.setTokensOut(tokensOut);
                    assistantMsg = qaMessagesRepository.save(assistantMsg);
                }

                if (contextAssembled != null && shouldExposeCitationSources(citationCfg)) {
                    List<RagContextPromptService.CitationSource> citedSources = resolveSourcesForOutput(
                            citationCfg,
                            contextAssembled.getSources(),
                            assistantMsg.getContent()
                    );
                    persistAssistantSources(assistantMsg.getId(), citedSources);
                }
            }
        } catch (Exception ex) {
            logger.error("ai_chat_regenerate_stream_failed userId={} sessionId={} model={}", currentUserId, session.getId(), model, ex);
            out.write("event: error\n");
            out.write("data: {\"message\":\"" + jsonEscape(String.valueOf(ex.getMessage())) + "\"}\n\n");
            out.flush();
        } finally {
            long latency = System.currentTimeMillis() - startedAt;
            out.write("event: done\n");
            out.write("data: {\"latencyMs\":" + latency + "}\n\n");
            out.flush();
        }
    }

    private void persistAssistantSources(Long answerMessageId, List<RagContextPromptService.CitationSource> sources) {
        if (answerMessageId == null) return;
        if (sources == null || sources.isEmpty()) return;
        try {
            int n = Math.min(200, sources.size());
            List<QaMessageSourcesEntity> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                RagContextPromptService.CitationSource s = sources.get(i);
                if (s == null) continue;
                QaMessageSourcesEntity e = new QaMessageSourcesEntity();
                e.setMessageId(answerMessageId);
                e.setSourceIndex(s.getIndex() != null ? s.getIndex() : (i + 1));
                e.setPostId(s.getPostId());
                e.setCommentId(s.getCommentId());
                e.setChunkIndex(s.getChunkIndex());
                e.setScore(s.getScore());
                e.setTitle(s.getTitle());
                e.setUrl(s.getUrl());
                rows.add(e);
            }
            if (!rows.isEmpty()) {
                qaMessageSourcesRepository.saveAll(rows);
            }
        } catch (Exception ex) {
            logger.warn("ai_chat_sources_persist_failed messageId={} err={}", answerMessageId, ex.getMessage());
        }
    }

    private void saveRetrievalHitsSafely(Long eventId, List<RetrievalHitsEntity> hits) {
        if (hits == null || hits.isEmpty()) return;
        sanitizeHitPostIds(hits);
        sanitizeHitChunkIds(hits);
        try {
            retrievalHitsRepository.saveAll(hits);
        } catch (Exception ex) {
            logger.warn("ai_chat_retrieval_hits_persist_failed eventId={} err={}", eventId, ex.getMessage());
        }
    }

    private void sanitizeHitPostIds(List<RetrievalHitsEntity> hits) {
        if (hits == null || hits.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        for (RetrievalHitsEntity h : hits) {
            if (h == null) continue;
            Long id = h.getPostId();
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return;
        List<Long> unique = ids.stream().distinct().toList();
        java.util.Set<Long> existing = new java.util.HashSet<>();
        for (var p : postsRepository.findAllById(unique)) {
            if (p != null && p.getId() != null) existing.add(p.getId());
        }
        if (existing.size() == unique.size()) return;
        for (RetrievalHitsEntity h : hits) {
            if (h == null) continue;
            Long id = h.getPostId();
            if (id != null && !existing.contains(id)) h.setPostId(null);
        }
    }

    private static int approxTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        double t = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x7f) t += 0.25;
            else t += 1.0;
        }
        return (int) Math.ceil(t);
    }

    private static String truncateByApproxTokens(String s, int maxTokens) {
        if (s == null) return "";
        if (maxTokens <= 0) return "";
        double t = 0;
        int end = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            t += (c <= 0x7f) ? 0.25 : 1.0;
            if (t > maxTokens) break;
            end = i + 1;
        }
        return s.substring(0, end);
    }

    private String resolvePromptText(String code) {
        if (code == null || code.isBlank()) return "";
        return promptsRepository.findByPromptCode(code)
                .map(PromptsEntity::getSystemPrompt)
                .orElse("");
    }
}
