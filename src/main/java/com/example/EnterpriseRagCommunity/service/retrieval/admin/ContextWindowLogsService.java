package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.ContextWindowDetailDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.ContextWindowLogDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.ContextWindowsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.ContextWindowsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContextWindowLogsService {

    private final ContextWindowsRepository contextWindowsRepository;
    private final RetrievalEventsRepository retrievalEventsRepository;

    @Transactional(readOnly = true)
    public Page<ContextWindowLogDTO> listWindows(LocalDateTime from, LocalDateTime to, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Pageable pageable = RetrievalLogPageSupport.pageable(page, size);
        LocalDateTime start = RetrievalLogPageSupport.defaultFrom(from, now);
        LocalDateTime end = RetrievalLogPageSupport.defaultTo(to, now);

        Page<ContextWindowsEntity> rows = contextWindowsRepository.findAll(
                (root, _query, cb) -> cb.between(root.get("createdAt"), start, end),
                pageable
        );

        List<Long> eventIds = rows.getContent().stream()
                .map(ContextWindowsEntity::getEventId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> queryByEventId = new HashMap<>();
        if (!eventIds.isEmpty()) {
            for (RetrievalEventsEntity ev : retrievalEventsRepository.findAllById(eventIds)) {
                if (ev == null || ev.getId() == null) continue;
                String qt = ev.getQueryText();
                queryByEventId.put(ev.getId(), qt == null ? null : qt.trim());
            }
        }

        return rows.map(e -> toLogDto(e, queryByEventId.get(e.getEventId())));
    }

    @Transactional(readOnly = true)
    public ContextWindowDetailDTO getWindow(long id) {
        ContextWindowsEntity e = contextWindowsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found"));
        String queryText = null;
        if (e.getEventId() != null) {
            queryText = retrievalEventsRepository.findById(e.getEventId()).map(RetrievalEventsEntity::getQueryText).orElse(null);
        }
        return toDetailDto(e, queryText);
    }

    private ContextWindowLogDTO toLogDto(ContextWindowsEntity e, String queryText) {
        ContextWindowLogDTO dto = new ContextWindowLogDTO();
        dto.setId(e.getId());
        dto.setEventId(e.getEventId());
        dto.setPolicy(e.getPolicy());
        dto.setBudgetTokens(e.getBudgetTokens());
        dto.setTotalTokens(e.getTotalTokens());
        dto.setSelectedItems(e.getSelectedItems());
        dto.setDroppedItems(e.getDroppedItems());
        dto.setItems(countItems(e.getChunkIds()));
        dto.setQueryText(queryText);
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private ContextWindowDetailDTO toDetailDto(ContextWindowsEntity e, String queryText) {
        ContextWindowDetailDTO dto = new ContextWindowDetailDTO();
        dto.setId(e.getId());
        dto.setEventId(e.getEventId());
        dto.setQueryText(queryText == null ? null : queryText.trim());
        dto.setPolicy(e.getPolicy());
        dto.setBudgetTokens(e.getBudgetTokens());
        dto.setTotalTokens(e.getTotalTokens());
        dto.setSelectedItems(e.getSelectedItems());
        dto.setDroppedItems(e.getDroppedItems());
        dto.setChunkIds(castMap(e.getChunkIds()));
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private static int countItems(Object chunkIds) {
        if (!(chunkIds instanceof Map<?, ?> m)) return 0;
        Object items = m.get("items");
        if (items instanceof List<?> l) return l.size();
        Object ids = m.get("ids");
        if (ids instanceof List<?> l2) return l2.size();
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }
}
