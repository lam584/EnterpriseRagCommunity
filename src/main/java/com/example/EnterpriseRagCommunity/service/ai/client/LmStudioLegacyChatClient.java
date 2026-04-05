package com.example.EnterpriseRagCommunity.service.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LmStudioLegacyChatClient {

    public record ChatRequest(
            String apiKey,
            String baseUrl,
            String model,
            String systemPrompt,
            String input,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 300_000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public LmStudioLegacyChatClient() {
    }

    public String chatOnce(ChatRequest req) throws IOException {
        String baseUrl = normalizeBaseUrl(req.baseUrl());
        String apiKey = normalizeString(req.apiKey());
        String model = normalizeString(req.model());
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");

        String endpoint = selectEndpoint(baseUrl);
        HttpURLConnection conn = AiClientHttpSupport.openJsonPost(
                endpoint,
                apiKey,
                req.extraHeaders(),
                req.connectTimeoutMs(),
                req.readTimeoutMs(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) body.put("system_prompt", req.systemPrompt());
        body.put("input", req.input() == null ? "" : req.input());

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

    private static String selectEndpoint(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/api/v1/chat")) return u;
        if (lower.endsWith("/api/v1")) return u + "/chat";
        return u + "/api/v1/chat";
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void applyHeaders(HttpURLConnection conn, String apiKey, Map<String, String> extraHeaders) {
        boolean hasAuth = false;
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k == null || k.isBlank() || v == null) continue;
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

    private static HttpURLConnection openJsonPost(
            String endpoint,
            String apiKey,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) throws IOException {
        return AiClientHttpSupport.openJsonPost(
                endpoint,
                apiKey,
                extraHeaders,
                connectTimeoutMs,
                readTimeoutMs,
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_READ_TIMEOUT_MS
        );
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl, null);
    }

    private static String normalizeBaseUrl(String baseUrl, String fallback) {
        String u = normalizeString(baseUrl, fallback);
        if (u == null) return "";
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String normalizeString(String s) {
        return normalizeString(s, null);
    }

    private static String normalizeString(String s, String fallback) {
        return RerankUrlSupport.normalizeString(s, fallback);
    }
}
