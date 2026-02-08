package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.config.AiProperties;
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

public class DashscopeRerankClient {

    public record RerankRequest(
            String apiKey,
            String baseUrl,
            String model,
            String query,
            List<?> documents,
            Integer topN,
            Boolean returnDocuments,
            String instruct,
            Double fps,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    private final AiProperties props;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public DashscopeRerankClient(AiProperties props) {
        this.props = props;
    }

    public String rerankOnce(RerankRequest req) throws IOException {
        String baseUrl = normalizeBaseUrl(req.baseUrl(), props.getBaseUrl());
        String apiKey = normalizeString(req.apiKey(), props.getApiKey());
        String model = normalizeString(req.model(), null);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        String query = normalizeString(req.query(), "");
        List<?> documents = req.documents() == null ? List.of() : req.documents();

        String endpoint = selectEndpoint(baseUrl, model);
        HttpURLConnection conn = openJsonPost(endpoint, apiKey, req.extraHeaders(), req.connectTimeoutMs(), req.readTimeoutMs());
        String body = objectMapper.writeValueAsString(buildBody(model, query, documents, req.topN(), req.returnDocuments(), req.instruct(), req.fps()));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
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

    private static Map<String, Object> buildBody(
            String model,
            String query,
            List<?> documents,
            Integer topN,
            Boolean returnDocuments,
            String instruct,
            Double fps
    ) {
        boolean compat = "qwen3-rerank".equalsIgnoreCase(model);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        if (compat) {
            body.put("documents", documents);
            body.put("query", query == null ? "" : query);
            if (topN != null && topN > 0) body.put("top_n", topN);
            if (instruct != null && !instruct.isBlank()) body.put("instruct", instruct);
            return body;
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query == null ? "" : query);
        input.put("documents", documents);
        body.put("input", input);

        Map<String, Object> parameters = new LinkedHashMap<>();
        if (topN != null && topN > 0) parameters.put("top_n", topN);
        if (returnDocuments != null) parameters.put("return_documents", Boolean.TRUE.equals(returnDocuments));
        if (instruct != null && !instruct.isBlank()) parameters.put("instruct", instruct);
        if (fps != null) parameters.put("fps", fps);
        if (!parameters.isEmpty()) body.put("parameters", parameters);
        return body;
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

        int cto = (connectTimeoutMs == null || connectTimeoutMs <= 0) ? props.getConnectTimeoutMs() : connectTimeoutMs;
        int rto = (readTimeoutMs == null || readTimeoutMs <= 0) ? props.getReadTimeoutMs() : readTimeoutMs;
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

    private static String selectEndpoint(String baseUrl, String model) {
        String m = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        if ("qwen3-rerank".equals(m)) {
            return compatApiBase(baseUrl) + "/reranks";
        }
        return dashscopeOrigin(baseUrl) + "/api/v1/services/rerank/text-rerank/text-rerank";
    }

    private static String compatApiBase(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/compatible-mode/v1")) {
            return u.substring(0, u.length() - "/compatible-mode/v1".length()) + "/compatible-api/v1";
        }
        return u;
    }

    private static String dashscopeOrigin(String baseUrl) {
        try {
            URL u = new URL(baseUrl);
            return u.getProtocol() + "://" + u.getAuthority();
        } catch (Exception e) {
            String u = baseUrl == null ? "" : baseUrl.trim();
            int scheme = u.indexOf("://");
            if (scheme >= 0) {
                int slash = u.indexOf('/', scheme + 3);
                if (slash > 0) return u.substring(0, slash);
            }
            return u;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String normalizeBaseUrl(String baseUrl, String fallback) {
        String u = normalizeString(baseUrl, fallback);
        if (u == null) return "";
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String normalizeString(String s, String fallback) {
        String t = s == null ? null : s.trim();
        if (t == null || t.isBlank()) return fallback;
        return t;
    }
}

