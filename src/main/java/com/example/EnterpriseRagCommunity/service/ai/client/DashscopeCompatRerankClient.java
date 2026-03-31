package com.example.EnterpriseRagCommunity.service.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashscopeCompatRerankClient {

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

    public DashscopeCompatRerankClient() {
    }

    public String rerankOnce(RerankRequest req) throws IOException {
        String root = normalizeRootUrl(req.baseUrl(), null);
        String apiKey = normalizeString(req.apiKey(), null);
        String model = normalizeString(req.model(), null);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");

        String endpoint = buildEndpoint(root, normalizeString(req.endpointPath(), "/compatible-api/v1/reranks"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", req.query() == null ? "" : req.query());
        body.put("documents", req.documents() == null ? List.of() : req.documents());
        if (req.topN() != null && req.topN() > 0) body.put("top_n", req.topN());
        if (req.returnDocuments() != null) body.put("return_documents", req.returnDocuments());
        if (req.instruct() != null && !req.instruct().isBlank()) body.put("instruct", req.instruct().trim());

        HttpURLConnection conn = openJsonPost(endpoint, apiKey, req.extraHeaders(), req.connectTimeoutMs(), req.readTimeoutMs());
        String json = objectMapper.writeValueAsString(body);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("Upstream returned HTTP " + code + " without body");
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            throw new IOException("Upstream returned HTTP " + code + ": " + resp);
        }
        return resp;
    }

    private HttpURLConnection openJsonPost(
            String endpoint,
            String apiKey,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        int cto = (connectTimeoutMs == null || connectTimeoutMs <= 0) ? DEFAULT_CONNECT_TIMEOUT_MS : connectTimeoutMs;
        int rto = (readTimeoutMs == null || readTimeoutMs <= 0) ? DEFAULT_READ_TIMEOUT_MS : readTimeoutMs;
        conn.setConnectTimeout(cto);
        conn.setReadTimeout(rto);

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        applyHeaders(conn, apiKey, extraHeaders);
        return conn;
    }

    private static void applyHeaders(HttpURLConnection conn, String apiKey, Map<String, String> extraHeaders) {
        boolean hasAuth = false;
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null || k.isBlank()) continue;
                if (v == null) continue;
                conn.setRequestProperty(k, v);
                if ("authorization".equals(k.trim().toLowerCase(Locale.ROOT))) {
                    hasAuth = true;
                }
            }
        }
        if (!hasAuth && apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
    }

    private static String buildEndpoint(String rootUrl, String pathOrUrl) {
        String p = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (!p.startsWith("/")) p = "/" + p;
        String root = rootUrl == null ? "" : rootUrl.trim();
        if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root + p;
    }

    private static String normalizeRootUrl(String baseUrl, String fallback) {
        String u = normalizeString(baseUrl, fallback);
        if (u == null) return "";
        u = u.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/api/v1")) return u.substring(0, Math.max(0, u.length() - 7));
        if (lower.endsWith("/v1")) return u.substring(0, Math.max(0, u.length() - 3));
        return u;
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String normalizeString(String s, String fallback) {
        String t = s == null ? null : s.trim();
        if (t == null || t.isBlank()) return fallback;
        return t;
    }
}
