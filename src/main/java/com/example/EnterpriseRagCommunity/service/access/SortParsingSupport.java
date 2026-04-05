package com.example.EnterpriseRagCommunity.service.access;

import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

public final class SortParsingSupport {

    private SortParsingSupport() {
    }

    public static Sort parseCreatedAtIdSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }

        String[] parts = sort.split(",");
        String field = parts.length > 0 ? parts[0].trim() : "createdAt";
        String dir = parts.length > 1 ? parts[1].trim() : "desc";
        if (!StringUtils.hasText(field)) {
            field = "createdAt";
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(dir);
        } catch (Exception ex) {
            direction = Sort.Direction.DESC;
        }

        return Sort.by(new Sort.Order(direction, field), new Sort.Order(Sort.Direction.DESC, "id"));
    }
}
