package com.example.EnterpriseRagCommunity.service.access;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

final class LogQuerySupport {

    private LogQuerySupport() {
    }

    static Pageable buildPageable(Sort sort) {
        int safePageSize = Math.clamp(999, 1, 200);
        return PageRequest.of(0, safePageSize, sort);
    }

    static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
