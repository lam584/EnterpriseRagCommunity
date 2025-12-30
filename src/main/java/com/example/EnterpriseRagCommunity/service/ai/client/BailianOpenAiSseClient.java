package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.config.AiProperties;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Minimal OpenAI-compatible SSE client for DashScope.
 *
 * It calls: POST {baseUrl}/chat/completions with stream=true and emits raw SSE lines.
 *
 * Note: We intentionally keep deps minimal (no extra HTTP libs).
 */
public class BailianOpenAiSseClient {

    public interface SseLineConsumer {
        void onLine(String line) throws IOException;
    }

    private final AiProperties props;

    public BailianOpenAiSseClient(AiProperties props) {
        this.props = props;
    }

    public void chatCompletionsStream(
            String apiKey,
            String baseUrl,
            String model,
            List<Map<String, String>> messages,
            Double temperature,
            SseLineConsumer consumer
    ) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = props.getBaseUrl();
        if (model == null || model.isBlank()) model = props.getModel();
        if (apiKey == null || apiKey.isBlank()) apiKey = props.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Missing app.ai.api-key (or env injection)");
        }

        String endpoint = baseUrl;
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        endpoint = endpoint + "/chat/completions";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(props.getConnectTimeoutMs());
        conn.setReadTimeout(props.getReadTimeoutMs());
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "text/event-stream");

        String body = buildBodyJson(model, messages, temperature);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            throw new IOException("Upstream returned HTTP " + code + " without body");
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                consumer.onLine(line);
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String buildBodyJson(String model, List<Map<String, String>> messages, Double temperature) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(escapeJson(model)).append("\"");
        sb.append(",\"stream\":true");
        if (temperature != null) {
            sb.append(",\"temperature\":").append(temperature);
        }
        sb.append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"role\":\"").append(escapeJson(m.getOrDefault("role", "user"))).append("\"");
            sb.append(",\"content\":\"").append(escapeJson(m.getOrDefault("content", ""))).append("\"");
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }
}

