package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

final class RetrievalLogPageSupport {

    private RetrievalLogPageSupport() {
    }

    static Pageable pageable(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 200);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    static LocalDateTime defaultFrom(LocalDateTime from, LocalDateTime now) {
        return from == null ? now.minusDays(7) : from;
    }

    static LocalDateTime defaultTo(LocalDateTime to, LocalDateTime now) {
        return to == null ? now.plusSeconds(1) : to;
    }
}
