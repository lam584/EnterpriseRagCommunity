package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatResponseDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
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
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final Logger logger = LoggerFactory.getLogger(AiChatService.class);
    private static final String ENV_DEFAULT = "default";

    private final AiProperties aiProperties;
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
    private final PortalChatConfigService portalChatConfigService;

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @Value("${app.upload.url-prefix:/uploads}")
    private String urlPrefix;

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
        String messageForHistory = images == null || images.isEmpty()
                ? req.getMessage()
                : appendImagesAsText(req.getMessage(), images);

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
                out.write("data: {\"message\":\"" + jsonEscape("数据操作失败：" + String.valueOf(ex.getMessage())) + "\"}\n\n");
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
        boolean deepThink = req.getDeepThink() != null ? Boolean.TRUE.equals(req.getDeepThink()) : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());
        String systemPrompt = deepThink ? portalCfg.getDeepThinkSystemPrompt() : portalCfg.getSystemPrompt();
        messages.add(ChatMessage.system(systemPrompt));
        String userSystemPrompt = loadUserDefaultSystemPrompt(currentUserId);
        if (userSystemPrompt != null) {
            messages.add(ChatMessage.system(userSystemPrompt));
        }

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0)
                ? req.getHistoryLimit()
                : (portalCfg.getHistoryLimit() != null && portalCfg.getHistoryLimit() > 0 ? portalCfg.getHistoryLimit() : 20);
        if (!req.getDryRun() && session.getId() != null && session.getId() > 0 && session.getContextStrategy() != ContextStrategy.NONE) {
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

        boolean useRag = req.getUseRag() != null ? Boolean.TRUE.equals(req.getUseRag()) : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.max(1, Math.min(50, ragTopKOverride));

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
                List<RagPostChatRetrievalService.Hit> postHits = List.of();
                List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
                if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
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

                boolean augmentEnabled = chatRagCfg == null || chatRagCfg.getEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getEnabled());
                if (augmentEnabled) {
                    boolean commentsEnabled = chatRagCfg == null || chatRagCfg.getCommentsEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getCommentsEnabled());
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
                            pol = null;
                        }
                    }
                    ac.setIncludePostContentPolicy(pol);
                    ragHits = ragChatPostCommentAggregationService.aggregate(req.getMessage(), postHits, commentHits, ac);
                } else {
                    ragHits = postHits;
                }
                if (!req.getDryRun()) {
                    RetrievalEventsEntity ev = new RetrievalEventsEntity();
                    ev.setUserId(currentUserId);
                    ev.setQueryText(req.getMessage());
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
                            appendCommentHits(outHits, retrievalEventId, RetrievalHitType.COMMENT_VEC, commentHits);
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                        } else if (ragHits != null && !ragHits.isEmpty()) {
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.VEC, postHits);
                            appendCommentHits(outHits, retrievalEventId, RetrievalHitType.COMMENT_VEC, commentHits);
                            if (augmentEnabled) {
                                appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                            }
                        }
                        if (!outHits.isEmpty()) {
                            sanitizeHitPostIds(outHits);
                            retrievalHitsRepository.saveAll(outHits);
                        }
                    }
                }

                if (ragHits != null && !ragHits.isEmpty()) {
                    contextAssembled = ragContextPromptService.assemble(req.getMessage(), ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (prompt != null && !prompt.isBlank()) {
                        messages.add(ChatMessage.system(prompt));
                    }
                    if (chatRagCfg != null && Boolean.TRUE.equals(chatRagCfg.getDebugEnabled())) {
                        writeRagDebugEvent(out, chatRagCfg, req.getMessage(), ragHits, commentHits, contextAssembled);
                    }
                    if (!req.getDryRun() && retrievalEventId != null && contextAssembled != null && contextCfg != null && Boolean.TRUE.equals(contextCfg.getLogEnabled())) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.max(0.0, Math.min(1.0, p))) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
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
        boolean hasImages = images != null && !images.isEmpty();
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
            boolean[] gotDelta = new boolean[] {false};
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
                gotDelta[0] = true;
                assistantAccum.append(delta);

                out.write("event: delta\n");
                out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                out.flush();
            };

            LlmGateway.RoutedChatStreamResult routed;
            LlmQueueTaskType chatTaskType = hasImages ? LlmQueueTaskType.IMAGE_CHAT : LlmQueueTaskType.TEXT_CHAT;
            ensureVisionModelForRequest(chatTaskType, providerIdOverride, modelOverride, hasImages);
            try {
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
            } catch (Exception ex) {
                throw ex;
            }
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
                List<RagContextPromptService.CitationSource> sources = contextAssembled.getSources();
                List<RagContextPromptService.CitationSource> citedSources = filterSourcesByCitations(
                        sources,
                        assistantAccum.toString()
                );

                String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, citedSources);
                if (sourcesText != null && !sourcesText.isBlank()) {
                    String delta = "\n\n" + sourcesText.trim();
                    assistantAccum.append(delta);
                    out.write("event: delta\n");
                    out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                    out.flush();
                }

                if (citedSources != null && !citedSources.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"sources\":[");
                    int n = Math.min(200, citedSources.size());
                    for (int i = 0; i < n; i++) {
                        RagContextPromptService.CitationSource s = citedSources.get(i);
                        if (s == null) continue;
                        if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
                        sb.append('{');
                        sb.append("\"index\":").append(s.getIndex() == null ? "null" : s.getIndex());
                        sb.append(",\"postId\":").append(s.getPostId() == null ? "null" : s.getPostId());
                        sb.append(",\"chunkIndex\":").append(s.getChunkIndex() == null ? "null" : s.getChunkIndex());
                        sb.append(",\"score\":").append(s.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", s.getScore()));
                        sb.append(",\"title\":\"").append(jsonEscape(s.getTitle() == null ? "" : s.getTitle())).append('"');
                        sb.append(",\"url\":\"").append(jsonEscape(s.getUrl() == null ? "" : s.getUrl())).append('"');
                        sb.append('}');
                    }
                    sb.append("]}");
                    out.write("event: sources\n");
                    out.write("data: " + sb + "\n\n");
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

                if (contextAssembled != null) {
                    List<RagContextPromptService.CitationSource> citedSources = filterSourcesByCitations(
                            contextAssembled.getSources(),
                            assistantMsg.getContent()
                    );
                    persistAssistantSources(assistantMsg.getId(), citedSources);
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
        String messageForHistory = images == null || images.isEmpty()
                ? req.getMessage()
                : appendImagesAsText(req.getMessage(), images);

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
        boolean deepThink = req.getDeepThink() != null ? Boolean.TRUE.equals(req.getDeepThink()) : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());
        String systemPrompt = deepThink ? portalCfg.getDeepThinkSystemPrompt() : portalCfg.getSystemPrompt();
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

        boolean useRag = req.getUseRag() != null ? Boolean.TRUE.equals(req.getUseRag()) : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.max(1, Math.min(50, ragTopKOverride));

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
                List<RagPostChatRetrievalService.Hit> postHits = List.of();
                List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
                if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
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

                boolean augmentEnabled = chatRagCfg == null || chatRagCfg.getEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getEnabled());
                if (augmentEnabled) {
                    boolean commentsEnabled = chatRagCfg == null || chatRagCfg.getCommentsEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getCommentsEnabled());
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
                            pol = null;
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
                            he.setDocumentId(h.getPostId());
                            he.setChunkId(h.getChunkIndex() == null ? null : h.getChunkIndex().longValue());
                            he.setScore(h.getScore() == null ? 0.0 : h.getScore());
                            he.setCreatedAt(LocalDateTime.now());
                            hitEntities.add(he);
                        }
                        if (!hitEntities.isEmpty()) retrievalHitsRepository.saveAll(hitEntities);
                    }

                    contextAssembled = ragContextPromptService.assemble(req.getMessage(), ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (prompt != null && !prompt.isBlank()) {
                        messages.add(1, ChatMessage.system(prompt));
                    }

                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null && contextAssembled != null && contextCfg != null
                            && Boolean.TRUE.equals(contextCfg.getLogEnabled())) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.max(0.0, Math.min(1.0, p))) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
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
        boolean hasImages = images != null && !images.isEmpty();
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
            boolean[] gotDelta = new boolean[] {false};
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
                gotDelta[0] = true;
                assistantAccum.append(delta);
            };

            LlmGateway.RoutedChatStreamResult routed;
            LlmQueueTaskType chatTaskType = hasImages ? LlmQueueTaskType.IMAGE_CHAT : LlmQueueTaskType.TEXT_CHAT;
            ensureVisionModelForRequest(chatTaskType, providerIdOverride, modelOverride, hasImages);
            try {
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
            } catch (Exception ex) {
                throw ex;
            }
            model = routed == null ? null : routed.model();

            if (deepThink && thinkOpen[0] && !thinkClosed[0]) {
                thinkClosed[0] = true;
                assistantAccum.append("</think>");
            }

            if (contextAssembled != null) {
                List<RagContextPromptService.CitationSource> sources = contextAssembled.getSources();
                citedSourcesForDto = filterSourcesByCitations(sources, assistantAccum.toString());

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

                if (contextAssembled != null) {
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

    public AiChatResponseDTO regenerateOnce(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId) {
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
                Long oldAnswerId = turn.getAnswerMessageId();
                turn.setAnswerMessageId(null);
                qaTurnsRepository.save(turn);
                qaMessagesRepository.deleteById(oldAnswerId);
            }
        }

        PortalChatConfigDTO.AssistantChatConfigDTO portalCfg = portalChatConfigService.getConfigOrDefault().getAssistantChat();
        List<ChatMessage> messages = new ArrayList<>();
        boolean deepThink = req.getDeepThink() != null ? Boolean.TRUE.equals(req.getDeepThink()) : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());
        String systemPrompt = deepThink ? portalCfg.getDeepThinkSystemPrompt() : portalCfg.getSystemPrompt();
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
        messages.add(ChatMessage.user(userMessageForModel));

        boolean useRag = req.getUseRag() != null ? Boolean.TRUE.equals(req.getUseRag()) : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.max(1, Math.min(50, ragTopKOverride));

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
                List<RagPostChatRetrievalService.Hit> postHits = List.of();
                if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
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

                boolean augmentEnabled = chatRagCfg == null || chatRagCfg.getEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getEnabled());
                if (augmentEnabled) {
                    boolean commentsEnabled = chatRagCfg == null || chatRagCfg.getCommentsEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getCommentsEnabled());
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
                            pol = null;
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
                            he.setDocumentId(h.getPostId());
                            he.setChunkId(h.getChunkIndex() == null ? null : h.getChunkIndex().longValue());
                            he.setScore(h.getScore() == null ? 0.0 : h.getScore());
                            he.setCreatedAt(LocalDateTime.now());
                            hitEntities.add(he);
                        }
                        if (!hitEntities.isEmpty()) retrievalHitsRepository.saveAll(hitEntities);
                    }

                    contextAssembled = ragContextPromptService.assemble(questionText, ragHits, contextCfg, citationCfg);
                    String prompt = contextAssembled == null ? null : contextAssembled.getContextPrompt();
                    if (prompt != null && !prompt.isBlank()) {
                        messages.add(1, ChatMessage.system(prompt));
                    }

                    if (!Boolean.TRUE.equals(req.getDryRun()) && retrievalEventId != null && contextAssembled != null && contextCfg != null
                            && Boolean.TRUE.equals(contextCfg.getLogEnabled())) {
                        double p = contextCfg.getLogSampleRate() == null ? 1.0 : contextCfg.getLogSampleRate();
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.max(0.0, Math.min(1.0, p))) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
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
                    LlmQueueTaskType.TEXT_CHAT,
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
                List<RagContextPromptService.CitationSource> sources = contextAssembled.getSources();
                citedSourcesForDto = filterSourcesByCitations(sources, assistantAccum.toString());

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

                if (contextAssembled != null) {
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

    private static List<AiChatResponseDTO.AiCitationSourceDTO> toCitationSourceDtos(List<RagContextPromptService.CitationSource> sources) {
        if (sources == null || sources.isEmpty()) return List.of();
        int n = Math.min(200, sources.size());
        List<AiChatResponseDTO.AiCitationSourceDTO> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RagContextPromptService.CitationSource s = sources.get(i);
            if (s == null) continue;
            AiChatResponseDTO.AiCitationSourceDTO dto = new AiChatResponseDTO.AiCitationSourceDTO();
            dto.setIndex(s.getIndex());
            dto.setPostId(s.getPostId());
            dto.setChunkIndex(s.getChunkIndex());
            dto.setScore(s.getScore());
            dto.setTitle(s.getTitle());
            dto.setUrl(s.getUrl());
            out.add(dto);
        }
        return out;
    }

    public void streamRegenerate(Long questionMessageId, AiChatRegenerateStreamRequest req, Long currentUserId, HttpServletResponse response)
            throws IOException {
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
                Long oldAnswerId = turn.getAnswerMessageId();
                turn.setAnswerMessageId(null);
                qaTurnsRepository.save(turn);
                qaMessagesRepository.deleteById(oldAnswerId);
            }
        }

        out.write("event: meta\n");
        out.write("data: {\"sessionId\":" + session.getId() + ",\"questionMessageId\":" + questionMsg.getId() + "}\n\n");
        out.flush();

        PortalChatConfigDTO.AssistantChatConfigDTO portalCfg = portalChatConfigService.getConfigOrDefault().getAssistantChat();
        List<ChatMessage> messages = new ArrayList<>();
        boolean deepThink = req.getDeepThink() != null ? Boolean.TRUE.equals(req.getDeepThink()) : Boolean.TRUE.equals(portalCfg.getDefaultDeepThink());
        String systemPrompt = deepThink ? portalCfg.getDeepThinkSystemPrompt() : portalCfg.getSystemPrompt();
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
        messages.add(ChatMessage.user(questionTextForModel));

        boolean useRag = req.getUseRag() != null ? Boolean.TRUE.equals(req.getUseRag()) : Boolean.TRUE.equals(portalCfg.getDefaultUseRag());
        Integer ragTopKOverride = req.getRagTopK() != null ? req.getRagTopK() : portalCfg.getRagTopK();
        int safeRagTopKOverride = ragTopKOverride == null ? 0 : Math.max(1, Math.min(50, ragTopKOverride));

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
                List<RagPostChatRetrievalService.Hit> postHits = List.of();
                List<RagCommentChatRetrievalService.Hit> commentHits = List.of();
                if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
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

                boolean augmentEnabled = chatRagCfg == null || chatRagCfg.getEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getEnabled());
                if (augmentEnabled) {
                    boolean commentsEnabled = chatRagCfg == null || chatRagCfg.getCommentsEnabled() == null || Boolean.TRUE.equals(chatRagCfg.getCommentsEnabled());
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
                            pol = null;
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
                            appendCommentHits(outHits, retrievalEventId, RetrievalHitType.COMMENT_VEC, commentHits);
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                        } else if (ragHits != null && !ragHits.isEmpty()) {
                            appendChatHits(outHits, retrievalEventId, RetrievalHitType.VEC, postHits);
                            appendCommentHits(outHits, retrievalEventId, RetrievalHitType.COMMENT_VEC, commentHits);
                            if (augmentEnabled) {
                                appendChatHits(outHits, retrievalEventId, RetrievalHitType.AGG, ragHits);
                            }
                        }
                        if (!outHits.isEmpty()) {
                            sanitizeHitPostIds(outHits);
                            retrievalHitsRepository.saveAll(outHits);
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
                        if (p >= 1.0 || ThreadLocalRandom.current().nextDouble() <= Math.max(0.0, Math.min(1.0, p))) {
                            ContextWindowsEntity cw = new ContextWindowsEntity();
                            cw.setEventId(retrievalEventId);
                            cw.setPolicy(contextAssembled.getPolicy());
                            cw.setTotalTokens(contextAssembled.getUsedTokens() == null ? 0 : contextAssembled.getUsedTokens());
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
                    LlmQueueTaskType.TEXT_CHAT,
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
                List<RagContextPromptService.CitationSource> sources = contextAssembled.getSources();
                List<RagContextPromptService.CitationSource> citedSources = filterSourcesByCitations(
                        sources,
                        assistantAccum.toString()
                );

                String sourcesText = RagContextPromptService.renderSourcesText(citationCfg, citedSources);
                if (sourcesText != null && !sourcesText.isBlank()) {
                    String delta = "\n\n" + sourcesText.trim();
                    assistantAccum.append(delta);
                    out.write("event: delta\n");
                    out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                    out.flush();
                }

                if (citedSources != null && !citedSources.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"sources\":[");
                    int n = Math.min(200, citedSources.size());
                    for (int i = 0; i < n; i++) {
                        RagContextPromptService.CitationSource s = citedSources.get(i);
                        if (s == null) continue;
                        if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
                        sb.append('{');
                        sb.append("\"index\":").append(s.getIndex() == null ? "null" : s.getIndex());
                        sb.append(",\"postId\":").append(s.getPostId() == null ? "null" : s.getPostId());
                        sb.append(",\"chunkIndex\":").append(s.getChunkIndex() == null ? "null" : s.getChunkIndex());
                        sb.append(",\"score\":").append(s.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", s.getScore()));
                        sb.append(",\"title\":\"").append(jsonEscape(s.getTitle() == null ? "" : s.getTitle())).append('"');
                        sb.append(",\"url\":\"").append(jsonEscape(s.getUrl() == null ? "" : s.getUrl())).append('"');
                        sb.append('}');
                    }
                    sb.append("]}");
                    out.write("event: sources\n");
                    out.write("data: " + sb + "\n\n");
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

                if (contextAssembled != null) {
                    List<RagContextPromptService.CitationSource> citedSources = filterSourcesByCitations(
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

    private static List<RagContextPromptService.CitationSource> filterSourcesByCitations(
            List<RagContextPromptService.CitationSource> sources,
            String answerText
    ) {
        if (sources == null || sources.isEmpty()) return List.of();
        if (answerText == null || answerText.isBlank()) return List.of();

        int maxIndex = 0;
        for (RagContextPromptService.CitationSource s : sources) {
            if (s == null || s.getIndex() == null) continue;
            maxIndex = Math.max(maxIndex, s.getIndex());
        }
        if (maxIndex <= 0) return List.of();

        Set<Integer> cited = extractCitationIndexes(answerText, maxIndex);
        if (cited.isEmpty()) return List.of();

        List<RagContextPromptService.CitationSource> out = new ArrayList<>();
        for (RagContextPromptService.CitationSource s : sources) {
            if (s == null || s.getIndex() == null) continue;
            if (cited.contains(s.getIndex())) out.add(s);
        }
        return out;
    }

    private static Set<Integer> extractCitationIndexes(String text, int maxIndex) {
        Set<Integer> out = new HashSet<>();
        if (text == null || text.isEmpty()) return out;

        boolean inFence = false;
        boolean inInlineCode = false;
        int n = text.length();

        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);

            if (c == '`') {
                if (i + 2 < n && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
                    inFence = !inFence;
                    i += 2;
                    continue;
                }
                if (!inFence) inInlineCode = !inInlineCode;
                continue;
            }

            if (inFence || inInlineCode) continue;
            if (c != '[') continue;

            int j = i + 1;
            int value = 0;
            int digits = 0;
            while (j < n && digits < 3) {
                char d = text.charAt(j);
                if (d < '0' || d > '9') break;
                value = value * 10 + (d - '0');
                digits++;
                j++;
            }
            if (digits == 0) continue;
            if (j >= n || text.charAt(j) != ']') continue;
            if (j + 1 < n && text.charAt(j + 1) == '(') continue;
            if (value <= 0 || value > maxIndex) continue;
            out.add(value);
        }

        return out;
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

    /**
     * Extremely small extractor to avoid adding JSON deps.
     * It looks for "\"content\":\"...\"" under choices[0].delta.
     *
     * This is not a full JSON parser but works for common OpenAI-compatible SSE frames.
     */
    static String extractDeltaContent(String json) {
        return sanitizeMarker(extractDeltaStringField(json, "content"));
    }

    static String extractDeltaReasoningContent(String json) {
        return sanitizeMarker(extractDeltaStringField(json, "reasoning_content"));
    }

    private static String sanitizeMarker(String s) {
        if (s == null) return null;
        if (s.equals("reasoning_content")) return "";
        return s;
    }

    static String extractDeltaStringField(String json, String field) {
        if (json == null) return null;
        String f = field == null ? "" : field.trim();
        if (f.isEmpty()) return null;
        int idx = json.indexOf("\"" + f + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int i = firstQuote + 1;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (esc) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception ignore) {
                            }
                            i += 4;
                        }
                    }
                    default -> sb.append(c);
                }
                esc = false;
            } else {
                if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            i++;
        }
        return sb.toString();
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
        return toNonBlank(aiProperties.getModel());
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

    private boolean providerSupportsVision(String providerId) {
        try {
            var p = llmGateway.resolve(providerId);
            if (p == null || p.metadata() == null) return false;
            Object v = p.metadata().get("supportsVision");
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) return "true".equalsIgnoreCase(s.trim());
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureVisionModelForRequest(LlmQueueTaskType taskType, String providerId, String modelOverride, boolean hasImages) {
        if (!hasImages) return;
        if (taskType != LlmQueueTaskType.IMAGE_CHAT) return;

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
                throw new IllegalArgumentException("未指定模型来源(providerId)，无法发送图片");
            }
            if (!isEnabledImageChatModel(effectiveProviderId, mo)) {
                throw new IllegalArgumentException("当前选择的模型不支持图片，请选择视觉模型（图片聊天）或切换为“自动(均衡负载)”");
            }
            return;
        }

        if (pid != null) {
            try {
                var p = llmGateway.resolve(pid);
                String effectiveProviderId = p == null ? null : toNonBlank(p.id());
                String effectiveModel = p == null ? null : toNonBlank(p.defaultChatModel());
                if (effectiveProviderId == null || effectiveModel == null) {
                    throw new IllegalArgumentException("未配置可用的默认模型，无法发送图片");
                }
                if (!isEnabledImageChatModel(effectiveProviderId, effectiveModel)) {
                    throw new IllegalArgumentException("当前选择的模型不支持图片，请选择视觉模型（图片聊天）或切换为“自动(均衡负载)”");
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("模型来源解析失败，无法发送图片");
            }
            return;
        }

        if (llmModelRepository.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(ENV_DEFAULT, "IMAGE_CHAT").isEmpty()) {
            throw new IllegalArgumentException("未配置“图片聊天(IMAGE_CHAT)”模型池，请在管理端为图片聊天配置视觉模型");
        }
    }

    private boolean isEnabledImageChatModel(String providerId, String modelName) {
        String pid = toNonBlank(providerId);
        String mn = toNonBlank(modelName);
        if (pid == null || mn == null) return false;
        return llmModelRepository.findByEnvAndProviderIdAndPurposeAndModelName(ENV_DEFAULT, pid, "IMAGE_CHAT", mn)
                .filter((e) -> !Boolean.FALSE.equals(e.getEnabled()))
                .isPresent();
    }

    private static List<AiChatStreamRequest.ImageInput> resolveImages(AiChatStreamRequest req) {
        if (req == null || req.getImages() == null || req.getImages().isEmpty()) return List.of();
        List<AiChatStreamRequest.ImageInput> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AiChatStreamRequest.ImageInput img : req.getImages()) {
            if (img == null) continue;
            if (out.size() >= 5) break;
            String url = toNonBlank(img.getUrl());
            if (url == null) continue;
            if (seen.contains(url)) continue;
            String mt = toNonBlank(img.getMimeType());
            boolean isImg = mt != null && mt.toLowerCase().startsWith("image/");
            if (!isImg && !isLikelyImageUrl(url)) continue;
            out.add(img);
            seen.add(url);
        }
        return out;
    }

    private static boolean isLikelyImageUrl(String url) {
        String u = toNonBlank(url);
        if (u == null) return false;
        String lower = u.toLowerCase();
        if (lower.startsWith("/uploads/")) return true;
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp")
                || lower.endsWith(".svg");
    }

    private static String appendImagesAsText(String userMsg, List<AiChatStreamRequest.ImageInput> images) {
        String base = userMsg == null ? "" : userMsg;
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n[IMAGES]\n");
        int take = 0;
        for (AiChatStreamRequest.ImageInput img : images) {
            if (img == null) continue;
            String url = toNonBlank(img.getUrl());
            if (url == null) continue;
            sb.append("- ").append(url).append("\n");
            take += 1;
            if (take >= 5) break;
        }
        return sb.toString();
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
            if (fileAssetId != null) {
                var fa = fileAssetsRepository.findById(fileAssetId).orElse(null);
                if (fa != null && fa.getPath() != null && !fa.getPath().isBlank()) {
                    Path p = Paths.get(fa.getPath()).toAbsolutePath().normalize();
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return Files.readAllBytes(p);
                    }
                }
            }

            String prefix = urlPrefix == null ? "/uploads" : urlPrefix.trim();
            String u = toNonBlank(url);
            if (u == null || prefix.isEmpty()) return null;
            if (!u.startsWith(prefix + "/")) return null;

            int q = u.indexOf('?');
            if (q >= 0) u = u.substring(0, q);
            String rel = u.substring(prefix.length());
            while (rel.startsWith("/")) rel = rel.substring(1);

            Path root = Paths.get(uploadRoot == null ? "uploads" : uploadRoot).toAbsolutePath().normalize();
            Path p = root.resolve(rel).normalize();
            if (!p.startsWith(root)) return null;
            if (!Files.exists(p) || !Files.isRegularFile(p)) return null;
            return Files.readAllBytes(p);
        } catch (Exception ignored) {
            return null;
        }
    }

    static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String buildRagContextPrompt(List<RagPostChatRetrievalService.Hit> hits, HybridRetrievalConfigDTO cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下为从社区帖子检索到的参考资料（仅供参考，回答时请结合用户问题，不要编造不存在的来源）：\n\n");
        int maxItems = cfg == null || cfg.getHybridK() == null ? 6 : Math.max(1, cfg.getHybridK());
        maxItems = Math.min(50, maxItems);
        int perDocMaxTokens = cfg == null || cfg.getPerDocMaxTokens() == null ? 4000 : Math.max(100, cfg.getPerDocMaxTokens());
        int maxInputTokens = cfg == null || cfg.getMaxInputTokens() == null ? 30000 : Math.max(1000, cfg.getMaxInputTokens());

        int usedTokens = 0;
        int n = Math.min(maxItems, hits == null ? 0 : hits.size());
        for (int i = 0; i < n; i++) {
            RagPostChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;
            sb.append('[').append(i + 1).append("] ");
            if (h.getPostId() != null) sb.append("post_id=").append(h.getPostId()).append(' ');
            if (h.getChunkIndex() != null) sb.append("chunk=").append(h.getChunkIndex()).append(' ');
            if (h.getScore() != null) sb.append("score=").append(String.format(Locale.ROOT, "%.4f", h.getScore())).append(' ');
            if (h.getTitle() != null && !h.getTitle().isBlank()) sb.append("\n标题：").append(h.getTitle().trim());
            sb.append('\n');
            String text = h.getContentText();
            if (text != null) {
                String t = truncateByApproxTokens(text.trim(), perDocMaxTokens);
                int tokens = approxTokens(t);
                if (usedTokens + tokens > maxInputTokens) break;
                usedTokens += tokens;
                sb.append(t);
            }
            sb.append("\n\n");
            if (sb.length() > 200_000) break;
        }
        return sb.toString().trim();
    }

    private static List<RagPostChatRetrievalService.Hit> toRagHits(List<HybridRagRetrievalService.DocHit> hits) {
        if (hits == null || hits.isEmpty()) return List.of();
        List<RagPostChatRetrievalService.Hit> out = new ArrayList<>();
        for (HybridRagRetrievalService.DocHit h : hits) {
            if (h == null) continue;
            RagPostChatRetrievalService.Hit rr = new RagPostChatRetrievalService.Hit();
            rr.setDocId(h.getDocId());
            Double s = h.getRerankScore();
            if (s == null) s = h.getFusedScore();
            if (s == null) s = h.getScore();
            rr.setScore(s);
            rr.setPostId(h.getPostId());
            rr.setChunkIndex(h.getChunkIndex());
            rr.setBoardId(h.getBoardId());
            rr.setTitle(h.getTitle());
            rr.setContentText(h.getContentText());
            out.add(rr);
        }
        return out;
    }

    private static void appendStageHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<HybridRagRetrievalService.DocHit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            HybridRagRetrievalService.DocHit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(type);
            rh.setDocumentId(h.getPostId());
            rh.setChunkId(null);
            Double s = h.getScore();
            if (type == RetrievalHitType.BM25 && h.getBm25Score() != null) s = h.getBm25Score();
            if (type == RetrievalHitType.VEC && h.getVecScore() != null) s = h.getVecScore();
            if (type == RetrievalHitType.RERANK && h.getRerankScore() != null) s = h.getRerankScore();
            if (s == null) s = 0.0;
            rh.setScore(s);
            out.add(rh);
        }
    }

    private static void appendChatHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<RagPostChatRetrievalService.Hit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            RagPostChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(type);
            rh.setDocumentId(h.getPostId());
            rh.setChunkId(null);
            rh.setScore(h.getScore() == null ? 0.0 : h.getScore());
            out.add(rh);
        }
    }

    private static void appendCommentHits(List<RetrievalHitsEntity> out, Long eventId, RetrievalHitType type, List<RagCommentChatRetrievalService.Hit> hits) {
        if (eventId == null || hits == null || hits.isEmpty()) return;
        int n = Math.min(1000, hits.size());
        for (int i = 0; i < n; i++) {
            RagCommentChatRetrievalService.Hit h = hits.get(i);
            if (h == null) continue;
            RetrievalHitsEntity rh = new RetrievalHitsEntity();
            rh.setEventId(eventId);
            rh.setRank(i + 1);
            rh.setHitType(type);
            rh.setDocumentId(h.getPostId());
            rh.setChunkId(h.getCommentId());
            rh.setScore(h.getScore() == null ? 0.0 : h.getScore());
            out.add(rh);
        }
    }

    private static void writeRagDebugEvent(
            PrintWriter out,
            ChatRagAugmentConfigDTO cfg,
            String queryText,
            List<RagPostChatRetrievalService.Hit> aggHits,
            List<RagCommentChatRetrievalService.Hit> commentHits,
            RagContextPromptService.AssembleResult contextAssembled
    ) {
        if (out == null || cfg == null || !Boolean.TRUE.equals(cfg.getDebugEnabled())) return;
        int maxChars = cfg.getDebugMaxChars() == null ? 4000 : Math.max(0, Math.min(200_000, cfg.getDebugMaxChars()));
        if (maxChars <= 0) return;

        Map<Long, RagPostChatRetrievalService.Hit> aggByPostId = new java.util.HashMap<>();
        if (aggHits != null) {
            for (RagPostChatRetrievalService.Hit h : aggHits) {
                if (h == null || h.getPostId() == null) continue;
                aggByPostId.putIfAbsent(h.getPostId(), h);
            }
        }

        List<RagContextPromptService.Item> selected = contextAssembled == null ? List.of() : (contextAssembled.getSelected() == null ? List.of() : contextAssembled.getSelected());
        int perItemMax = Math.max(200, Math.min(2000, maxChars / Math.max(1, selected.size())));

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"query\":\"").append(jsonEscape(queryText == null ? "" : queryText)).append('"');

        sb.append(",\"selected\":[");
        int totalPreview = 0;
        for (int i = 0; i < selected.size(); i++) {
            RagContextPromptService.Item it = selected.get(i);
            if (it == null) continue;
            if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
            sb.append('{');
            sb.append("\"rank\":").append(it.getRank() == null ? "null" : it.getRank());
            sb.append(",\"postId\":").append(it.getPostId() == null ? "null" : it.getPostId());
            sb.append(",\"score\":").append(it.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", it.getScore()));
            String preview = "";
            if (it.getPostId() != null) {
                RagPostChatRetrievalService.Hit h = aggByPostId.get(it.getPostId());
                String t = h == null ? null : h.getContentText();
                if (t != null) {
                    String trimmed = t.trim();
                    int remain = Math.max(0, maxChars - totalPreview);
                    int cap = Math.min(perItemMax, remain);
                    if (cap > 0) {
                        preview = trimmed.length() <= cap ? trimmed : trimmed.substring(0, cap);
                        totalPreview += preview.length();
                    }
                }
            }
            sb.append(",\"preview\":\"").append(jsonEscape(preview)).append('"');
            sb.append('}');
            if (totalPreview >= maxChars) break;
        }
        sb.append(']');

        sb.append(",\"commentHits\":[");
        int n = Math.min(50, commentHits == null ? 0 : commentHits.size());
        for (int i = 0; i < n; i++) {
            RagCommentChatRetrievalService.Hit h = commentHits.get(i);
            if (h == null) continue;
            if (sb.charAt(sb.length() - 1) != '[') sb.append(',');
            sb.append('{');
            sb.append("\"commentId\":").append(h.getCommentId() == null ? "null" : h.getCommentId());
            sb.append(",\"postId\":").append(h.getPostId() == null ? "null" : h.getPostId());
            sb.append(",\"score\":").append(h.getScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", h.getScore()));
            sb.append('}');
        }
        sb.append(']');

        sb.append('}');

        out.write("event: rag_debug\n");
        out.write("data: " + sb + "\n\n");
        out.flush();
    }

    private void sanitizeHitPostIds(List<RetrievalHitsEntity> hits) {
        if (hits == null || hits.isEmpty()) return;
        List<Long> ids = new ArrayList<>();
        for (RetrievalHitsEntity h : hits) {
            if (h == null) continue;
            Long id = h.getDocumentId();
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
            Long id = h.getDocumentId();
            if (id != null && !existing.contains(id)) h.setDocumentId(null);
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
}
