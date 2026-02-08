package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaMessageDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaCitationSourceDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSearchHitDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessageSourcesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaTurnsEntity;
import com.example.EnterpriseRagCommunity.entity.rag.enums.MessageRole;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessageSourcesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaMessagesRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaSessionsRepository;
import com.example.EnterpriseRagCommunity.repository.rag.QaTurnsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QaHistoryService {

    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;
    private final QaMessageSourcesRepository qaMessageSourcesRepository;

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

        if (req.getTitle() != null) {
            String t = req.getTitle().trim();
            if (t.isEmpty()) t = null;
            s.setTitle(t);
        }
        if (req.getIsActive() != null) {
            s.setIsActive(req.getIsActive());
        }
        QaSessionsEntity saved = qaSessionsRepository.save(s);
        return toSessionDTO(saved);
    }

    public Page<QaSearchHitDTO> searchMyHistory(Long userId, String q, int page, int size) {
        String query = (q == null ? "" : q.trim());
        if (query.isEmpty()) {
            return Page.empty();
        }

        // BOOLEAN MODE: append * for prefix matching to make UX friendlier.
        String booleanQ = toBooleanModeQuery(query);

        Pageable pageable = PageRequest.of(page, size);

        Page<QaSessionsEntity> sessionHits = qaSessionsRepository.searchByTitleFulltext(userId, booleanQ, pageable);
        Page<QaMessagesEntity> messageHits = qaMessagesRepository.searchMyMessagesFulltext(userId, booleanQ, pageable);

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

        int from = Math.min(page * size, merged.size());
        int to = Math.min(from + size, merged.size());
        List<QaSearchHitDTO> slice = merged.subList(from, to);

        return new org.springframework.data.domain.PageImpl<>(slice, pageable, merged.size());
    }

    @Transactional
    public void deleteMySession(Long userId, Long sessionId) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("session not found"));

        // 子表先删，避免外键/逻辑引用
        qaTurnsRepository.deleteBySessionId(s.getId());
        qaMessagesRepository.deleteBySessionId(s.getId());
        qaSessionsRepository.deleteById(s.getId());
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
                    .replaceAll("[+\\-~<>*()\\\"@]", " ") // remove boolean operators / special chars
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
}
