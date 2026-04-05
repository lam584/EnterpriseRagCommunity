package com.example.EnterpriseRagCommunity.service.ai.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.Map;

final class AiClientHttpSupport {

    private AiClientHttpSupport() {
    }

    static HttpURLConnection openJsonPost(
            String endpoint,
            String apiKey,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            int defaultConnectTimeoutMs,
            int defaultReadTimeoutMs
    ) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(endpoint).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        int cto = (connectTimeoutMs == null || connectTimeoutMs <= 0) ? defaultConnectTimeoutMs : connectTimeoutMs;
        int rto = (readTimeoutMs == null || readTimeoutMs <= 0) ? defaultReadTimeoutMs : readTimeoutMs;
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
}
