package com.example.EnterpriseRagCommunity.service.ai.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RerankUrlSupport {

    private RerankUrlSupport() {
    }

    static String trimTrailingSlash(String baseUrl, String fallback) {
        String url = normalizeString(baseUrl, fallback);
        if (url == null) {
            return "";
        }
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    static String lowerCase(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    static String normalizeString(String value, String fallback) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isBlank()) {
            return fallback;
        }
        return trimmed;
    }

    static String buildEndpoint(String baseUrl, String pathOrUrl) {
        String p = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (!p.startsWith("/")) p = "/" + p;
        String root = baseUrl == null ? "" : baseUrl.trim();
        if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root + p;
    }

    static Map<String, Object> buildBaseRequestBody(String model, String query, List<String> documents) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", query == null ? "" : query);
        body.put("documents", documents == null ? List.of() : documents);
        return body;
    }
}
