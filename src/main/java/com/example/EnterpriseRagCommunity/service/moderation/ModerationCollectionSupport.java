package com.example.EnterpriseRagCommunity.service.moderation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModerationCollectionSupport {

    private ModerationCollectionSupport() {
    }

    public static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object it : list) {
                if (it == null) continue;
                String s = String.valueOf(it).trim();
                if (!s.isBlank()) out.add(s);
            }
            return out;
        }
        String s = String.valueOf(v).trim();
        return s.isBlank() ? List.of() : List.of(s);
    }

    public static Map<String, Object> asObjectMap(Object v) {
        if (!(v instanceof Map<?, ?> mm)) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : mm.entrySet()) {
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }
}
