package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.QaMessageDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSearchHitDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionDTO;
import com.example.EnterpriseRagCommunity.dto.ai.QaSessionUpdateRequest;
import com.example.EnterpriseRagCommunity.entity.rag.QaMessagesEntity;
import com.example.EnterpriseRagCommunity.entity.rag.QaSessionsEntity;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class QaHistoryService {

    private final QaSessionsRepository qaSessionsRepository;
    private final QaMessagesRepository qaMessagesRepository;
    private final QaTurnsRepository qaTurnsRepository;

    public Page<QaSessionDTO> listMySessions(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return qaSessionsRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toSessionDTO);
    }

    public List<QaMessageDTO> getMySessionMessages(Long userId, Long sessionId) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));
        // ensure active
        if (Boolean.FALSE.equals(s.getIsActive())) {
            throw new IllegalArgumentException("session inactive");
        }
        return qaMessagesRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toMessageDTO)
                .toList();
    }

    @Transactional
    public QaSessionDTO updateMySession(Long userId, Long sessionId, QaSessionUpdateRequest req) {
        QaSessionsEntity s = qaSessionsRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("session not found"));

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
                .orElseThrow(() -> new IllegalArgumentException("session not found"));

        // 子表先删，避免外键/逻辑引用
        qaTurnsRepository.deleteBySessionId(s.getId());
        qaMessagesRepository.deleteBySessionId(s.getId());
        qaSessionsRepository.deleteById(s.getId());
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

