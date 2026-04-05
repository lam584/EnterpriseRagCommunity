package com.example.EnterpriseRagCommunity.service.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaCitationSourceDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaCompressContextResultDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaMessageDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSearchHitDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QaHistoryService {

    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;
    private final QaMessageSourcesRepository qaMessageSourcesRepository;
    private final ChatContextGovernanceConfigService chatContextGovernanceConfigService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;
    private final AdministratorService administratorService;

    public Page<QaSessionDTO> listMySessions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return qaSessionsRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toSessionDTO);
    }

    public List<QaMessageDTO> getMySessionMessages(Long userId, Long sessionId) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        // ensure active
        if (Boolean.FALSE.equals(s.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }
        List<QaMessagesEntity> msgs = qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        Map<Long, QaTurnsEntity> turnByAnswerMessageId = new HashMap<>();
        List<QaTurnsEntity> turns = qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        for (QaTurnsEntity t : turns) {
            if (t == null || t.getAnswerMessageId() == null) continue;
            turnByAnswerMessageId.put(t.getAnswerMessageId(), t);
        }

        Map<Long, List<QaCitationSourceDTO>> sourcesByMessageId = new HashMap<>();
        List<Long> assistantMessageIds = msgs.stream()
                .filter(m -> m.getRole() == MessageRole.ASSISTANT)
                .map(QaMessagesEntity::getId)
                .toList();
        if (!assistantMessageIds.isEmpty()) {
            List<QaMessageSourcesEntity> rows = qaMessageSourcesRepository
                    .findByMessageIdInOrderByMessageIdAscSourceIndexAsc(assistantMessageIds);
            for (QaMessageSourcesEntity r : rows) {
                if (r == null || r.getMessageId() == null) continue;
                sourcesByMessageId.computeIfAbsent(r.getMessageId(), k -> new ArrayList<>()).add(toSourceDTO(r));
            }
        }

        List<QaMessageDTO> out = new ArrayList<>(msgs.size());
        for (QaMessagesEntity e : msgs) {
            QaMessageDTO dto = toMessageDTO(e);
            if (e.getRole() == MessageRole.ASSISTANT) {
                dto.setSources(sourcesByMessageId.getOrDefault(e.getId(), List.of()));
                QaTurnsEntity t = turnByAnswerMessageId.get(e.getId());
                if (t != null) {
                    dto.setLatencyMs(t.getLatencyMs());
                    dto.setFirstTokenLatencyMs(t.getFirstTokenLatencyMs());
                }
            } else {
                dto.setSources(null);
            }
            out.add(dto);
        }
        return out;
    }

    @Transactional
    public QaSessionDTO updateMySession(Long userId, Long sessionId, QaSessionUpdateRequest req) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        Map<String, Object> before = summarizeSessionForAudit(s);

        if (req.getTitle() != null) {
            String t = req.getTitle().trim();
            if (t.isEmpty()) t = null;
            s.setTitle(t);
        }
        if (req.getIsActive() != null) {
            s.setIsActive(req.getIsActive());
        }
        QaSessionsEntity saved = qaSessionsRepository.save(s);
        auditLogWriter.write(
                userId,
                resolveActorNameOrNull(userId),
                "QA_SESSION_UPDATE",
                "QA_SESSION",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新对话会话",
                null,
                auditDiffBuilder.build(before, summarizeSessionForAudit(saved))
        );
        return toSessionDTO(saved);
    }

    @Transactional
    public QaCompressContextResultDTO compressMySessionContext(Long userId, Long sessionId) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));
        if (Boolean.FALSE.equals(s.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }

        ChatContextGovernanceConfigDTO cfg = chatContextGovernanceConfigService.getConfigOrDefault();
        int keepLast = cfg == null || cfg.getCompressionKeepLastMessages() == null ? 0 : Math.max(0, cfg.getCompressionKeepLastMessages());

        QaCompressContextResultDTO out = new QaCompressContextResultDTO();
        out.setSessionId(sessionId);
        out.setKeptLast(keepLast);

        if (cfg == null || Boolean.FALSE.equals(cfg.getCompressionEnabled())) {
            out.setSummaryMessageId(null);
            out.setCompressedDeletedCount(0);
            out.setSummary("");
            auditLogWriter.write(
                    userId,
                    resolveActorNameOrNull(userId),
                    "QA_CONTEXT_COMPRESS",
                    "QA_SESSION",
                    sessionId,
                    AuditResult.SUCCESS,
                    "压缩对话上下文",
                    null,
                    summarizeCompressResultForAudit(out)
            );
            return out;
        }

        List<QaMessagesEntity> msgs = qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int systemPrefix = 0;
        while (systemPrefix < msgs.size()) {
            QaMessagesEntity m = msgs.get(systemPrefix);
            if (m == null || m.getRole() != MessageRole.SYSTEM) break;
            systemPrefix++;
        }

        int available = msgs.size() - systemPrefix;
        if (available <= keepLast) {
            out.setSummaryMessageId(null);
            out.setCompressedDeletedCount(0);
            out.setSummary("");
            auditLogWriter.write(
                    userId,
                    resolveActorNameOrNull(userId),
                    "QA_CONTEXT_COMPRESS",
                    "QA_SESSION",
                    sessionId,
                    AuditResult.SUCCESS,
                    "压缩对话上下文",
                    null,
                    summarizeCompressResultForAudit(out)
            );
            return out;
        }

        int compressCount = available - keepLast;
        List<QaMessagesEntity> toCompress = new ArrayList<>(msgs.subList(systemPrefix, systemPrefix + compressCount));

        int snippetChars = cfg.getCompressionPerMessageSnippetChars() == null ? 200 : Math.max(10, cfg.getCompressionPerMessageSnippetChars());
        int maxChars = cfg.getCompressionMaxChars() == null ? 8000 : Math.max(200, cfg.getCompressionMaxChars());
        String summary = buildSummaryFromEntities(toCompress, snippetChars, maxChars);
        if (summary == null || summary.isBlank()) {
            out.setSummaryMessageId(null);
            out.setCompressedDeletedCount(0);
            out.setSummary("");
            auditLogWriter.write(
                    userId,
                    resolveActorNameOrNull(userId),
                    "QA_CONTEXT_COMPRESS",
                    "QA_SESSION",
                    sessionId,
                    AuditResult.SUCCESS,
                    "压缩对话上下文",
                    null,
                    summarizeCompressResultForAudit(out)
            );
            return out;
        }

        QaMessagesEntity summaryMsg = new QaMessagesEntity();
        summaryMsg.setSessionId(sessionId);
        summaryMsg.setRole(MessageRole.SYSTEM);
        summaryMsg.setContent(summary);
        summaryMsg.setCreatedAt(toCompress.getFirst() != null ? toCompress.getFirst().getCreatedAt() : java.time.LocalDateTime.now());
        summaryMsg = qaMessagesRepository.save(summaryMsg);

        List<Long> deleteIds = toCompress.stream()
                .filter(Objects::nonNull)
                .map(QaMessagesEntity::getId)
                .filter(Objects::nonNull)
                .toList();

        List<Long> deletedAssistantIds = toCompress.stream()
                .filter(m -> m != null && m.getRole() == MessageRole.ASSISTANT && m.getId() != null)
                .map(QaMessagesEntity::getId)
                .toList();
        if (!deletedAssistantIds.isEmpty()) {
            qaMessageSourcesRepository.deleteByMessageIdIn(deletedAssistantIds);
        }

        if (!deleteIds.isEmpty()) {
            List<QaTurnsEntity> turns = qaTurnsRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            for (QaTurnsEntity t : turns) {
                if (t == null) continue;
                Long qid = t.getQuestionMessageId();
                Long aid = t.getAnswerMessageId();
                if ((qid != null && deleteIds.contains(qid)) || (aid != null && deleteIds.contains(aid))) {
                    qaTurnsRepository.delete(t);
                }
            }
            qaMessagesRepository.deleteAllById(deleteIds);
        }

        out.setSummaryMessageId(summaryMsg.getId());
        out.setCompressedDeletedCount(deleteIds.size());
        out.setSummary(summary);
        auditLogWriter.write(
                userId,
                resolveActorNameOrNull(userId),
                "QA_CONTEXT_COMPRESS",
                "QA_SESSION",
                sessionId,
                AuditResult.SUCCESS,
                "压缩对话上下文",
                null,
                summarizeCompressResultForAudit(out)
        );
        return out;
    }

    public Page<QaSearchHitDTO> searchMyHistory(Long userId, String q, int page, int size) {
        String query = (q == null ? "" : q.trim());
        if (query.isEmpty()) {
            return Page.empty();
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 200);

        // BOOLEAN MODE: append * for prefix matching to make UX friendlier.
        String booleanQ = toBooleanModeQuery(query);

        int from = safePage * safeSize;
        int fetchLimit = Math.clamp(safeSize * 2L, from + safeSize, 2000);
        Pageable fetchPageable = PageRequest.of(0, fetchLimit);

        Page<QaSessionsEntity> sessionHits = qaSessionsRepository.searchByTitleFulltext(userId, booleanQ, fetchPageable);
        Page<QaMessagesEntity> messageHits = qaMessagesRepository.searchMyMessagesFulltext(userId, booleanQ, fetchPageable);

        // merge into a simple Page-like structure (best-effort). For v1, concatenate and cap size.
        List<QaSearchHitDTO> merged = new ArrayList<>();
        for (QaSessionsEntity s : sessionHits.getContent()) {
            QaSearchHitDTO hit = new QaSearchHitDTO();
            hit.setType("SESSION_TITLE");
            hit.setSessionId(s.getId());
            hit.setTitle(s.getTitle());
            hit.setSnippet(s.getTitle());
            hit.setCreatedAt(s.getCreatedAt());
            merged.add(hit);
        }
        for (QaMessagesEntity m : messageHits.getContent()) {
            QaSearchHitDTO hit = new QaSearchHitDTO();
            hit.setType("MESSAGE");
            hit.setSessionId(m.getSessionId());
            hit.setMessageId(m.getId());
            hit.setSnippet(snippet(m.getContent(), 180));
            hit.setCreatedAt(m.getCreatedAt());
            merged.add(hit);
        }

        // sort by createdAt desc
        merged.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        int safeFrom = Math.min(from, merged.size());
        int to = Math.min(safeFrom + safeSize, merged.size());
        long total = Math.max(0L, sessionHits.getTotalElements()) + Math.max(0L, messageHits.getTotalElements());
        List<QaSearchHitDTO> slice = merged.subList(safeFrom, to);

        return new org.springframework.data.domain.PageImpl<>(slice, PageRequest.of(safePage, safeSize), total);
    }

    @Transactional
    public void deleteMySession(Long userId, Long sessionId) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));

        // 子表先删，避免外键/逻辑引用
        qaTurnsRepository.deleteBySessionId(s.getId());
        qaMessagesRepository.deleteBySessionId(s.getId());
        qaSessionsRepository.deleteById(s.getId());
        auditLogWriter.write(
                userId,
                resolveActorNameOrNull(userId),
                "QA_SESSION_DELETE",
                "QA_SESSION",
                sessionId,
                AuditResult.SUCCESS,
                "删除对话会话",
                null,
                summarizeSessionForAudit(s)
        );
    }

    private String resolveActorNameOrNull(Long userId) {
        if (userId == null) return null;
        try {
            return administratorService.findById(userId).map(UsersEntity::getEmail).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> summarizeSessionForAudit(QaSessionsEntity e) {
        Map<String, Object> m = new HashMap<>();
        if (e == null) return m;
        m.put("id", e.getId());
        m.put("titleLen", e.getTitle() == null ? 0 : e.getTitle().length());
        m.put("contextStrategy", e.getContextStrategy());
        m.put("isActive", e.getIsActive());
        return m;
    }

    private static Map<String, Object> summarizeCompressResultForAudit(QaCompressContextResultDTO out) {
        Map<String, Object> m = new HashMap<>();
        if (out == null) return m;
        m.put("sessionId", out.getSessionId());
        m.put("keptLast", out.getKeptLast());
        m.put("summaryMessageId", out.getSummaryMessageId());
        m.put("compressedDeletedCount", out.getCompressedDeletedCount());
        String summary = out.getSummary();
        m.put("summaryLen", summary == null ? 0 : summary.length());
        return m;
    }

    public Page<QaMessageDTO> listMyFavoriteMessages(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return qaMessagesRepository.findMyFavorites(userId, pageable)
                .map(this::toMessageDTO);
    }

    private QaSessionDTO toSessionDTO(QaSessionsEntity e) {
        QaSessionDTO dto = new QaSessionDTO();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setContextStrategy(e.getContextStrategy());
        dto.setIsActive(e.getIsActive());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private QaMessageDTO toMessageDTO(QaMessagesEntity e) {
        QaMessageDTO dto = new QaMessageDTO();
        dto.setId(e.getId());
        dto.setSessionId(e.getSessionId());
        dto.setRole(e.getRole());
        dto.setContent(e.getContent());
        dto.setModel(e.getModel());
        dto.setTokensIn(e.getTokensIn());
        dto.setTokensOut(e.getTokensOut());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setIsFavorite(e.getIsFavorite());
        return dto;
    }

    private static QaCitationSourceDTO toSourceDTO(QaMessageSourcesEntity e) {
        QaCitationSourceDTO dto = new QaCitationSourceDTO();
        dto.setIndex(e.getSourceIndex());
        dto.setPostId(e.getPostId());
        dto.setCommentId(e.getCommentId());
        dto.setChunkIndex(e.getChunkIndex());
        dto.setScore(e.getScore());
        dto.setTitle(e.getTitle());
        dto.setUrl(e.getUrl());
        return dto;
    }

    static String toBooleanModeQuery(String raw) {
        // Split by whitespace; convert token -> +token*
        String[] parts = raw.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            String token = p
                    .replaceAll("[+\\-~<>*()\"@]", " ") // remove boolean operators / special chars
                    .trim();
            if (token.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append('+').append(token).append('*');
        }
        return sb.toString();
    }

    static String snippet(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "…";
    }

    private static String buildSummaryFromEntities(List<QaMessagesEntity> msgs, int snippetChars, int maxChars) {
        if (msgs == null || msgs.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("以下为较早历史消息摘要（自动压缩，仅供参考，必要时请向用户确认）：\n");
        for (QaMessagesEntity m : msgs) {
            if (m == null) continue;
            MessageRole role = m.getRole();
            if (role == MessageRole.SYSTEM) continue;
            String text = m.getContent();
            if (text == null || text.isBlank()) continue;
            String oneLine = text.replace('\r', ' ').replace('\n', ' ').trim();
            if (oneLine.length() > snippetChars) oneLine = oneLine.substring(0, snippetChars);
            String prefix = role == MessageRole.USER ? "U: " : (role == MessageRole.ASSISTANT ? "A: " : (String.valueOf(role).toLowerCase(Locale.ROOT) + ": "));
            sb.append("- ").append(prefix).append(oneLine).append('\n');
            if (sb.length() >= maxChars) break;
        }
        String s = sb.toString().trim();
        if (s.length() > maxChars) s = s.substring(0, maxChars);
        return s;
    }
}
