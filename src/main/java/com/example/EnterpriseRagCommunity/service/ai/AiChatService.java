package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.ai.AiChatStreamRequest;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.ContextStrategy;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import com.example.EnterpriseRagCommunity.service.ai.client.BailianOpenAiSseClient;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostChatRetrievalService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final Logger logger = LoggerFactory.getLogger(AiChatService.class);

    private final AiProperties aiProperties;
    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;
    private final RagPostChatRetrievalService ragRetrievalService;
    private final RetrievalEventsRepository retrievalEventsRepository;
    private final RetrievalHitsRepository retrievalHitsRepository;

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
        messages.add(Map.of("role", "system", "content", "你是一个严谨、专业的中文助手。"));

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
        try {
            ragHits = ragRetrievalService.retrieve(req.getMessage(), 6, null);
            if (!req.getDryRun()) {
                RetrievalEventsEntity ev = new RetrievalEventsEntity();
                ev.setUserId(currentUserId);
                ev.setQueryText(req.getMessage());
                ev.setBm25K(0);
                ev.setVecK(6);
                ev.setHybridK(null);
                ev.setRerankModel(null);
                ev.setRerankK(null);
                ev.setCreatedAt(LocalDateTime.now());
                ev = retrievalEventsRepository.save(ev);
                retrievalEventId = ev.getId();

                if (retrievalEventId != null && ragHits != null && !ragHits.isEmpty()) {
                    List<RetrievalHitsEntity> outHits = new ArrayList<>();
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
                    retrievalHitsRepository.saveAll(outHits);
                }
            }

            if (ragHits != null && !ragHits.isEmpty()) {
                messages.add(Map.of("role", "system", "content", buildRagContextPrompt(ragHits)));
            }
        } catch (Exception ex) {
            logger.warn("ai_chat_rag_retrieval_failed userId={} sessionId={} err={}", currentUserId, session.getId(), ex.getMessage());
        }

        messages.add(Map.of("role", "user", "content", req.getMessage()));

        StringBuilder assistantAccum = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        String model = (req.getModel() != null && !req.getModel().isBlank()) ? req.getModel() : aiProperties.getModel();

        QaMessagesEntity assistantMsg = null;

        try {
            sseClient().chatCompletionsStream(
                    null,
                    aiProperties.getBaseUrl(),
                    model,
                    messages,
                    req.getTemperature(),
                    line -> {
                        if (line == null || line.isBlank()) return;
                        if (!line.startsWith("data:")) return;

                        String data = line.substring("data:".length()).trim();
                        if ("[DONE]".equals(data)) {
                            out.write("event: done\n");
                            out.write("data: {}\n\n");
                            out.flush();
                            return;
                        }

                        String delta = extractDeltaContent(data);
                        if (delta == null || delta.isEmpty()) return;

                        assistantAccum.append(delta);

                        out.write("event: delta\n");
                        out.write("data: {\"content\":\"" + jsonEscape(delta) + "\"}\n\n");
                        out.flush();
                    }
            );

            // finalize persistence
            if (!req.getDryRun()) {
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
                    qaTurnsRepository.save(turn);
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

    private static String buildRagContextPrompt(List<RagPostChatRetrievalService.Hit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下为从社区帖子检索到的参考资料（仅供参考，回答时请结合用户问题，不要编造不存在的来源）：\n\n");
        int n = Math.min(6, hits == null ? 0 : hits.size());
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
                String t = text.trim();
                if (t.length() > 600) t = t.substring(0, 600) + "...";
                sb.append(t);
            }
            sb.append("\n\n");
            if (sb.length() > 6000) break;
        }
        return sb.toString().trim();
    }
}

