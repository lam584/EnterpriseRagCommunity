package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalEventLogDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.RetrievalHitLogDTO;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalEventsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalEventsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.RetrievalHitsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HybridRetrievalLogsService {

    private final RetrievalEventsRepository retrievalEventsRepository;
    private final RetrievalHitsRepository retrievalHitsRepository;

    @Transactional(readOnly = true)
    public Page<RetrievalEventLogDTO> listEvents(LocalDateTime from, LocalDateTime to, int page, int size) {
        LocalDateTime now = LocalDateTime.now();
        Pageable pageable = RetrievalLogPageSupport.pageable(page, size);
        LocalDateTime start = RetrievalLogPageSupport.defaultFrom(from, now);
        LocalDateTime end = RetrievalLogPageSupport.defaultTo(to, now);

        return retrievalEventsRepository.findAll(
                (root, _query, cb) -> cb.between(root.get("createdAt"), start, end),
                pageable
        ).map(this::toEventDto);
    }

    @Transactional(readOnly = true)
    public List<RetrievalHitLogDTO> listHits(long eventId) {
        List<RetrievalHitsEntity> rows = retrievalHitsRepository.findByEventId(eventId);
        List<RetrievalHitLogDTO> out = new ArrayList<>();
        for (RetrievalHitsEntity e : rows) {
            out.add(toHitDto(e));
        }
        out.sort(Comparator.comparingInt(a -> a.getRank() == null ? Integer.MAX_VALUE : a.getRank()));
        return out;
    }

    private RetrievalEventLogDTO toEventDto(RetrievalEventsEntity e) {
        RetrievalEventLogDTO dto = new RetrievalEventLogDTO();
        dto.setId(e.getId());
        dto.setUserId(e.getUserId());
        dto.setQueryText(trimOrNull(e.getQueryText()));
        dto.setBm25K(e.getBm25K());
        dto.setVecK(e.getVecK());
        dto.setHybridK(e.getHybridK());
        dto.setRerankModel(trimOrNull(e.getRerankModel()));
        dto.setRerankK(e.getRerankK());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private RetrievalHitLogDTO toHitDto(RetrievalHitsEntity e) {
        RetrievalHitLogDTO dto = new RetrievalHitLogDTO();
        dto.setId(e.getId());
        dto.setEventId(e.getEventId());
        dto.setRank(e.getRank());
        dto.setHitType(e.getHitType());
        dto.setPostId(e.getPostId());
        dto.setChunkId(e.getChunkId());
        dto.setScore(e.getScore());
        return dto;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = Objects.toString(s, "").trim();
        return t.isBlank() ? null : t;
    }
}
