package com.example.EnterpriseRagCommunity.service.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocalRerankClient {

    public record RerankRequest(
            String apiKey,
            String baseUrl,
            String rerankEndpointPath,
            String model,
            String query,
            List<String> documents,
            Integer topN,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 300_000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public LocalRerankClient() {
    }

    public String rerankOnce(RerankRequest req) throws IOException {
        String baseUrl = normalizeRerankBaseUrl(req.baseUrl(), null);
        String apiKey = RerankUrlSupport.normalizeString(req.apiKey(), null);
        String model = RerankUrlSupport.normalizeString(req.model(), null);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");

        Map<String, Object> body = RerankRequestBodySupport.buildBaseBody(model, req.query(), req.documents(), req.topN());

        String endpoint = buildEndpoint(baseUrl, req.rerankEndpointPath());
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

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("Upstream returned HTTP " + code + " without body");
        String resp = readAll(is);
        if (code < 200 || code >= 300) {
            throw new IOException("Upstream returned HTTP " + code + ": " + resp);
        }
        return resp;
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String normalizeRerankBaseUrl(String baseUrl, String fallback) {
        String u = RerankUrlSupport.trimTrailingSlash(baseUrl, fallback);
        String lower = RerankUrlSupport.lowerCase(u);
        if (lower.endsWith("/v1") || lower.contains("/v1/")) return u;
        if (lower.endsWith("/api/v1") || lower.contains("/api/v1/")) return u;
        return u + "/v1";
    }

    private static String buildEndpoint(String baseUrlWithV1, String rerankEndpointPath) {
        String base = baseUrlWithV1 == null ? "" : baseUrlWithV1.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String path = RerankUrlSupport.normalizeString(rerankEndpointPath, "/rerank");
        if (path == null || path.isBlank()) path = "/rerank";
        return RerankUrlSupport.buildEndpoint(base, path);
    }
}
