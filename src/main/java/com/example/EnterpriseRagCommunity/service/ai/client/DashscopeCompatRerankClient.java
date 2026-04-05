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
        String apiKey = RerankUrlSupport.normalizeString(req.apiKey(), null);
        String model = RerankUrlSupport.normalizeString(req.model(), null);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");

        String endpoint = buildEndpoint(root, RerankUrlSupport.normalizeString(req.endpointPath(), "/compatible-api/v1/reranks"));

        Map<String, Object> body = RerankRequestBodySupport.buildBaseBody(model, req.query(), req.documents(), req.topN());
        if (req.returnDocuments() != null) body.put("return_documents", req.returnDocuments());
        if (req.instruct() != null && !req.instruct().isBlank()) body.put("instruct", req.instruct().trim());

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

    private static String buildEndpoint(String rootUrl, String pathOrUrl) {
        String p = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (!p.startsWith("/")) p = "/" + p;
        String root = rootUrl == null ? "" : rootUrl.trim();
        if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root + p;
    }

    private static String normalizeRootUrl(String baseUrl, String fallback) {
        String u = RerankUrlSupport.trimTrailingSlash(baseUrl, fallback);
        String lower = RerankUrlSupport.lowerCase(u);
        if (lower.endsWith("/api/v1")) return u.substring(0, Math.max(0, u.length() - 7));
        if (lower.endsWith("/v1")) return u.substring(0, Math.max(0, u.length() - 3));
        return u;
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
