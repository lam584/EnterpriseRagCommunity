package com.example.EnterpriseRagCommunity.service.access;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditDiffBuilder {
    private final ObjectMapper objectMapper;

    public Map<String, Object> build(Object before, Object after) {
        Map<String, Object> b0 = toMapOrEmpty(before);
        Map<String, Object> a0 = toMapOrEmpty(after);

        Map<String, Object> b = sanitizeMap(b0);
        Map<String, Object> a = sanitizeMap(a0);

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(b.keySet());
        keys.addAll(a.keySet());

        List<Map<String, Object>> changes = new ArrayList<>();
        for (String k : keys) {
            Object bv = b.get(k);
            Object av = a.get(k);
            if (jsonEquals(bv, av)) continue;

            Map<String, Object> ch = new LinkedHashMap<>();
            ch.put("field", k);
            ch.put("from", bv);
            ch.put("to", av);
            changes.add(ch);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("before", b);
        out.put("after", a);
        out.put("changes", changes);
        return out;
    }

    private Map<String, Object> toMapOrEmpty(Object v) {
        if (v == null) return Map.of();
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                if (e.getKey() == null) continue;
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        try {
            return objectMapper.convertValue(v, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ignore) {
            return Map.of();
        }
    }

    private boolean jsonEquals(Object a, Object b) {
        if (Objects.equals(a, b)) return true;
        try {
            JsonNode an = objectMapper.valueToTree(a);
            JsonNode bn = objectMapper.valueToTree(b);
            return an.equals(bn);
        } catch (Exception ignore) {
            return false;
        }
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k == null) continue;
            out.put(k, isSensitiveKey(k) ? "***" : v);
        }
        return out;
    }

    private static boolean isSensitiveKey(String k) {
        String s = k.toLowerCase(Locale.ROOT);
        return s.contains("password")
                || s.contains("secret")
                || s.contains("token")
                || s.contains("apikey")
                || s.contains("api_key")
                || s.contains("accesskey")
                || s.contains("access_key")
                || s.contains("privatekey")
                || s.contains("private_key");
    }
}
