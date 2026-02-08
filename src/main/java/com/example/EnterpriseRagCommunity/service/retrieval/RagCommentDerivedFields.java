package com.example.EnterpriseRagCommunity.service.retrieval;

import java.util.Map;
import java.util.function.LongFunction;

public final class RagCommentDerivedFields {

    private RagCommentDerivedFields() {
    }

    public static String excerpt(String content, int maxChars) {
        String s = content == null ? "" : content.trim();
        if (s.isBlank()) return null;
        int n = Math.max(1, maxChars);
        if (s.length() <= n) return s;
        return s.substring(0, n) + "...";
    }

    public static int computeLevel(Long parentId, LongFunction<Long> parentResolver) {
        if (parentId == null) return 0;
        int depth = 1;
        Long cur = parentId;
        while (cur != null && depth < 20) {
            cur = parentResolver == null ? null : parentResolver.apply(cur);
            if (cur != null) depth++;
        }
        return depth;
    }

    public static int nextFloor(Map<Long, Integer> perPostMaxFloor, Long postId, Integer existingMaxFloor) {
        if (postId == null) return 0;
        if (perPostMaxFloor == null) return 0;
        Integer cur = perPostMaxFloor.get(postId);
        if (cur == null) {
            cur = existingMaxFloor == null ? 0 : Math.max(0, existingMaxFloor);
        }
        int next = cur + 1;
        perPostMaxFloor.put(postId, next);
        return next;
    }
}

