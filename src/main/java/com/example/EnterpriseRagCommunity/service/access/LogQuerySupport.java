package com.example.EnterpriseRagCommunity.service.access;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

final class LogQuerySupport {

    private LogQuerySupport() {
    }

    static Pageable buildPageable(Integer page, Integer pageSize, int defaultPageSize, int maxPageSize, Sort sort) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safePageSize = pageSize == null ? defaultPageSize : Math.min(Math.max(pageSize, 1), maxPageSize);
        return PageRequest.of(safePage - 1, safePageSize, sort);
    }

    static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
