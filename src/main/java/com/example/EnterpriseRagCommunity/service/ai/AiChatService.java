package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatRegenerateStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse;
import com.example.EnterpriseRagCommunity.dto.retrieval.CitationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextClipConfigDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.CitationConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.ContextClipConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final Logger logger = LoggerFactory.getLogger(AiChatService.class);

    private final AiProperties aiProperties;
    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;
    private final RagPostChatRetrievalService ragRetrievalService;
    private final HybridRetrievalConfigService hybridRetrievalConfigService;
    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final ContextClipConfigService contextClipConfigService;
    private final CitationConfigService citationConfigService;
    private final RagContextPromptService ragContextPromptService;
    private final RetrievalEventsRepository retrievalEventsRepository;
    private final RetrievalHitsRepository retrievalHitsRepository;
    private final ContextWindowsRepository contextWindowsRepository;
    private final PostsRepository postsRepository;
    private final QaMessageSourcesRepository qaMessageSourcesRepository;
    private final OpenSearchTokenizeService openSearchTokenizeService;

    private BailianOpenAiSseClient sseClient() {
        return new BailianOpenAiSseClient(aiProperties);
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

        // 3) persist user msg + turn shell
        QaMessagesEntity userMsg = null;
        QaTurnsEntity turn = null;
        if (!req.getDryRun()) {
            try {
                userMsg = new QaMessagesEntity();
                userMsg.setSessionId(session.getId());
                userMsg.setRole(MessageRole.USER);
                userMsg.setContent(req.getMessage());
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
        List<Map<String, String>> messages = new ArrayList<>();
        boolean deepThink = Boolean.TRUE.equals(req.getDeepThink());
        String systemPrompt = deepThink
                ? "你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。"
                : "你是一个严谨、专业的中文助手。";
        messages.add(Map.of("role", "system", "content", systemPrompt));

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0) ? req.getHistoryLimit() : 20;
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
                messages.add(Map.of("role", role, "content", m.getContent()));
            }
        }

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
        Long retrievalEventId = null;
        HybridRetrievalConfigDTO hybridCfg = null;
        HybridRagRetrievalService.RetrieveResult hybridResult = null;
        ContextClipConfigDTO contextCfg = null;
        CitationConfigDTO citationCfg = null;
        RagContextPromptService.AssembleResult contextAssembled = null;
        try {
            contextCfg = contextClipConfigService.getConfigOrDefault();
            citationCfg = citationConfigService.getConfigOrDefault();

            hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
            if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
                hybridResult = hybridRagRetrievalService.retrieve(req.getMessage(), null, hybridCfg, false);
                ragHits = toRagHits(hybridResult == null ? null : hybridResult.getFinalHits());
            } else {
                int k = contextCfg == null || contextCfg.getMaxItems() == null ? 6 : Math.max(1, contextCfg.getMaxItems());
                ragHits = ragRetrievalService.retrieve(req.getMessage(), Math.min(50, k), null);
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
                    } else if (ragHits != null && !ragHits.isEmpty()) {
                        for (int i = 0; i < ragHits.size(); i++) {
                            RagPostChatRetrievalService.Hit h = ragHits.get(i);
                            RetrievalHitsEntity rh = new RetrievalHitsEntity();
                            rh.setEventId(retrievalEventId);
                            rh.setRank(i + 1);
                            rh.setHitType(RetrievalHitType.VEC);
                            rh.setDocumentId(h == null ? null : h.getPostId());
                            rh.setChunkId(null);
                            rh.setScore(h == null || h.getScore() == null ? 0.0 : h.getScore());
                            outHits.add(rh);
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
                    messages.add(Map.of("role", "system", "content", prompt));
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
        } catch (Exception ex) {
            logger.warn("ai_chat_rag_retrieval_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        messages.add(Map.of("role", "user", "content", req.getMessage()));

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAtMs = new long[] {0L};
        String model = (req.getModel() != null && !req.getModel().isBlank()) ? req.getModel() : aiProperties.getModel();
        Double temperature = req.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;

        QaMessagesEntity assistantMsg = null;

        try {
            sseClient().chatCompletionsStream(
                    null,
                    aiProperties.getBaseUrl(),
                    model,
                    messages,
                    temperature,
                    line -> {
                        if (line == null || line.isBlank()) return;
                        if (!line.startsWith("data:")) return;

                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            return;
                        }

                        String delta = extractDeltaContent(data);
                        if (delta == null || delta.isEmpty()) return;

                        if (firstDeltaAtMs[0] == 0L) firstDeltaAtMs[0] = System.currentTimeMillis();
                        assistantAccum.append(delta);

                        out.write("event: delta\n");
                        out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                        out.flush();
                    }
            );

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
                Integer userTokensIn = tryTokenCountText(req.getMessage());
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

                Integer tokensIn = tryTokenCountChatMessages(messages);
                Integer tokensOut = tryTokenCountText(assistantAccum.toString());
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

        List<Map<String, String>> messages = new ArrayList<>();
        boolean deepThink = Boolean.TRUE.equals(req.getDeepThink());
        String systemPrompt = deepThink
                ? "你是一个严谨、专业的中文助手。请在回答前进行更充分的推理与自检，输出更可靠、结构化的结论；不确定时说明不确定并给出验证建议。"
                : "你是一个严谨、专业的中文助手。";
        messages.add(Map.of("role", "system", "content", systemPrompt));

        int historyLimit = (req.getHistoryLimit() != null && req.getHistoryLimit() > 0) ? req.getHistoryLimit() : 20;
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
                messages.add(Map.of("role", role, "content", m.getContent()));
            }
        }

        String questionText = questionMsg.getContent();
        messages.add(Map.of("role", "user", "content", questionText));

        List<RagPostChatRetrievalService.Hit> ragHits = List.of();
        Long retrievalEventId = null;
        HybridRetrievalConfigDTO hybridCfg = null;
        HybridRagRetrievalService.RetrieveResult hybridResult = null;
        ContextClipConfigDTO contextCfg = null;
        CitationConfigDTO citationCfg = null;
        RagContextPromptService.AssembleResult contextAssembled = null;
        try {
            contextCfg = contextClipConfigService.getConfigOrDefault();
            citationCfg = citationConfigService.getConfigOrDefault();

            hybridCfg = hybridRetrievalConfigService.getConfigOrDefault();
            if (hybridCfg != null && Boolean.TRUE.equals(hybridCfg.getEnabled())) {
                hybridResult = hybridRagRetrievalService.retrieve(questionText, null, hybridCfg, false);
                ragHits = toRagHits(hybridResult == null ? null : hybridResult.getFinalHits());
            } else {
                int k = contextCfg == null || contextCfg.getMaxItems() == null ? 6 : Math.max(1, contextCfg.getMaxItems());
                ragHits = ragRetrievalService.retrieve(questionText, Math.min(50, k), null);
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
                    } else if (ragHits != null && !ragHits.isEmpty()) {
                        for (int i = 0; i < ragHits.size(); i++) {
                            RagPostChatRetrievalService.Hit h = ragHits.get(i);
                            RetrievalHitsEntity rh = new RetrievalHitsEntity();
                            rh.setEventId(retrievalEventId);
                            rh.setRank(i + 1);
                            rh.setHitType(RetrievalHitType.VEC);
                            rh.setDocumentId(h == null ? null : h.getPostId());
                            rh.setChunkId(null);
                            rh.setScore(h == null || h.getScore() == null ? 0.0 : h.getScore());
                            outHits.add(rh);
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
                    messages.add(1, Map.of("role", "system", "content", prompt));
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
        } catch (Exception ex) {
            logger.warn("ai_chat_regenerate_rag_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        long[] firstDeltaAtMs = new long[] {0L};
        String model = (req.getModel() != null && !req.getModel().isBlank()) ? req.getModel() : aiProperties.getModel();
        Double temperature = req.getTemperature();
        if (temperature == null && deepThink) temperature = 0.2;

        QaMessagesEntity assistantMsg = null;

        try {
            sseClient().chatCompletionsStream(
                    null,
                    aiProperties.getBaseUrl(),
                    model,
                    messages,
                    temperature,
                    line -> {
                        if (line == null || line.isBlank()) return;
                        if (!line.startsWith("data:")) return;

                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            return;
                        }

                        String delta = extractDeltaContent(data);
                        if (delta == null || delta.isEmpty()) return;

                        if (firstDeltaAtMs[0] == 0L) firstDeltaAtMs[0] = System.currentTimeMillis();
                        assistantAccum.append(delta);

                        out.write("event: delta\n");
                        out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                        out.flush();
                    }
            );

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
                Integer userTokensIn = tryTokenCountText(questionText);
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

                Integer tokensIn = tryTokenCountChatMessages(messages);
                Integer tokensOut = tryTokenCountText(assistantAccum.toString());
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

    private Integer tryTokenCountText(String text) {
        String t = text == null ? null : text.trim();
        if (t == null || t.isEmpty()) return null;
        try {
            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setText(t);
            OpenSearchTokenizeResponse resp = openSearchTokenizeService.tokenize(req);
            if (resp == null || resp.getUsage() == null) return null;
            return resp.getUsage().getInputTokens();
        } catch (Exception ex) {
            logger.warn("ai_chat_token_count_text_failed err={}", ex.getMessage());
            return null;
        }
    }

    private Integer tryTokenCountChatMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return null;
        try {
            List<OpenSearchTokenizeRequest.Message> list = new ArrayList<>(messages.size());
            for (Map<String, String> m : messages) {
                if (m == null) continue;
                String role = m.get("role");
                if (role == null || role.isBlank()) continue;
                OpenSearchTokenizeRequest.Message mm = new OpenSearchTokenizeRequest.Message();
                mm.setRole(role);
                mm.setContent(m.getOrDefault("content", ""));
                list.add(mm);
            }
            if (list.isEmpty()) return null;
            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setMessages(list);
            OpenSearchTokenizeResponse resp = openSearchTokenizeService.tokenize(req);
            if (resp == null || resp.getUsage() == null) return null;
            return resp.getUsage().getInputTokens();
        } catch (Exception ex) {
            logger.warn("ai_chat_token_count_messages_failed err={}", ex.getMessage());
            return null;
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
        if (json == null) return null;
        // try: "delta":{"content":"..."}
        int idx = json.indexOf("\"content\"");
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
