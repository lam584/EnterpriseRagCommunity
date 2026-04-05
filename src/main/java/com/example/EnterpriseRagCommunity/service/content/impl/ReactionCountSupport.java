package com.example.EnterpriseRagCommunity.service.content.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReactionCountSupport {

    private ReactionCountSupport() {
    }

    static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        if (rows == null) return map;
        for (Object[] row : rows) {
            if (row == null || row.length < 2) continue;
            Long targetId = row[0] == null ? null : ((Number) row[0]).longValue();
            Long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            if (targetId != null) map.put(targetId, count);
        }
        return map;
    }
}
