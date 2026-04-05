package com.example.EnterpriseRagCommunity.service.moderation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModerationMapPathSupport {

    private ModerationMapPathSupport() {
    }

    public static Object deepGet(Map<String, Object> root, String path) {
        if (root == null || root.isEmpty() || path == null || path.isBlank()) return null;
        String[] segs = path.split("\\.");
        Object cur = root;
        for (String seg : segs) {
            if (seg == null || seg.isBlank()) continue;
            Map<String, Object> m = asMap(cur);
            if (m == null) return null;
            cur = m.get(seg);
        }
        return cur;
    }

    public static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : source.entrySet()) {
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }
}