package com.example.EnterpriseRagCommunity.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableSupport {

    private PageableSupport() {
    }

    public static Pageable oneBased(Integer page, Integer pageSize, Sort sort) {
        int pageNumber = page != null && page > 0 ? page - 1 : 0;
        int size = pageSize != null && pageSize > 0 ? pageSize : 20;
        return PageRequest.of(pageNumber, size, sort);
    }
}
