package com.example.EnterpriseRagCommunity.service.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ResponsesStyleRerankClient {

    public record RerankRequest(
            String apiKey,
            String baseUrl,
            String endpointPath,
            String model,
            String query,
            List<String> documents,
            Integer topN,
            Boolean returnDocuments,
            String instruct,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 300_000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public ResponsesStyleRerankClient() {
    }

    public String rerankOnce(RerankRequest req) throws IOException {
        String baseUrlWithV1 = normalizeBaseUrlWithV1(req.baseUrl());
        String apiKey = normalizeString(req.apiKey(), null);
        String model = normalizeString(req.model(), null);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");

        String endpoint = buildEndpoint(baseUrlWithV1, normalizeString(req.endpointPath(), "/v1/rerank"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", req.query() == null ? "" : req.query());
        payload.put("documents", req.documents() == null ? List.of() : req.documents());
        if (req.topN() != null && req.topN() > 0) payload.put("top_n", req.topN());
        if (req.returnDocuments() != null) payload.put("return_documents", req.returnDocuments());
        if (req.instruct() != null && !req.instruct().isBlank()) payload.put("instruct", req.instruct().trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", payload);
        body.put("max_output_tokens", 1024);

        HttpURLConnection conn = AiClientHttpSupport.openJsonPost(
                endpoint,
                apiKey,
                req.extraHeaders(),
                req.connectTimeoutMs(),
                req.readTimeoutMs(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS
        );
        String json = objectMapper.writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        return RerankHttpResponseSupport.readJsonResponse(conn);
    }

    private static String normalizeBaseUrlWithV1(String baseUrl) {
        String u = normalizeString(baseUrl, null);
        if (u == null) return "";
        u = u.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/v1") || lower.contains("/v1/")) return u;
        if (lower.endsWith("/api/v1") || lower.contains("/api/v1/")) return u;
        return u + "/v1";
    }

    private static String buildEndpoint(String baseUrlWithV1, String endpointPath) {
        String base = baseUrlWithV1 == null ? "" : baseUrlWithV1.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String path = normalizeString(endpointPath, "/v1/rerank");
        if (path == null || path.isBlank()) path = "/v1/rerank";
        path = path.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        if (!path.startsWith("/")) path = "/" + path;
        if (path.startsWith("/v1/") || path.equals("/v1") || path.startsWith("/api/v1/") || path.equals("/api/v1")) {
            String root = base;
            String lower = root.toLowerCase(Locale.ROOT);
            if (lower.endsWith("/v1")) root = root.substring(0, root.length() - 3);
            else if (lower.endsWith("/api/v1")) root = root.substring(0, root.length() - 7);
            if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
            return root + path;
        }
        return base + path;
    }

    private static String normalizeString(String s, String fallback) {
        String t = s == null ? null : s.trim();
        if (t == null || t.isBlank()) return fallback;
        return t;
    }
}
