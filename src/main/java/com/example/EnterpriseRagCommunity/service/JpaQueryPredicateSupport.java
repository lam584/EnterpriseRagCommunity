package com.example.EnterpriseRagCommunity.service;

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public final class JpaQueryPredicateSupport {

    private JpaQueryPredicateSupport() {
    }

    public static <T> void addEqualIfPresent(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Path<T> path,
            T value
    ) {
        if (value != null) {
            predicates.add(cb.equal(path, value));
        }
    }
}
