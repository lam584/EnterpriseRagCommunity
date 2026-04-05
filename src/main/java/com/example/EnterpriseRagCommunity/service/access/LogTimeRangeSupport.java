package com.example.EnterpriseRagCommunity.service.access;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.LocalDateTime;
import java.util.List;

final class LogTimeRangeSupport {

    private LogTimeRangeSupport() {
    }

    static void addCreatedAtBetween(
            List<Predicate> predicates,
            Root<?> root,
            CriteriaBuilder criteriaBuilder,
            LocalDateTime createdFrom,
            LocalDateTime createdTo
    ) {
        if (predicates == null || root == null || criteriaBuilder == null) {
            return;
        }
        if (createdFrom == null && createdTo == null) {
            return;
        }
        LocalDateTime start = createdFrom == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : createdFrom;
        LocalDateTime end = createdTo == null ? LocalDateTime.now().plusYears(100) : createdTo;
        predicates.add(criteriaBuilder.between(root.get("createdAt"), start, end));
    }
}
