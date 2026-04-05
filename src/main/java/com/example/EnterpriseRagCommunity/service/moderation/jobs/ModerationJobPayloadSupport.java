package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import java.util.Map;

final class ModerationJobPayloadSupport {

    private ModerationJobPayloadSupport() {
    }

    static Object deepGet(Map<String, Object> payload, String path) {
        if (payload == null || path == null || path.isBlank()) {
            return null;
        }
        String[] segments = path.split("\\.");
        Object current = payload;
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    static Boolean deepGetBoolean(Map<String, Object> payload, String path) {
        Object value = deepGet(payload, path);
        if (value instanceof Boolean bool) return bool;
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
        return null;
    }
}
