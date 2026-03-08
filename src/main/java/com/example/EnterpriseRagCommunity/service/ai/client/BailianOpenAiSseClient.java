package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 300_000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public BailianOpenAiSseClient() {
    }

    public void chatCompletionsStream(
            String apiKey,
            String baseUrl,
            String model,
            List<ChatMessage> messages,
            Double temperature,
            SseLineConsumer consumer
    ) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl 不能为空");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        if (apiKey == null) apiKey = "";

        String endpoint = baseUrl;
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        endpoint = endpoint + "/chat/completions";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
        if (apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
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

    /**
     * Non-streaming completion: POST /chat/completions with stream=false.
     *
     * @return raw response json (OpenAI compatible)
     */
    public String chatCompletionsOnce(
            String apiKey,
            String baseUrl,
            String model,
            List<ChatMessage> messages,
            Double temperature
    ) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl 不能为空");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model 不能为空");
        if (apiKey == null) apiKey = "";

        String endpoint = baseUrl;
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        endpoint = endpoint + "/chat/completions";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
        if (apiKey != null && !apiKey.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");

        String body = buildBodyJsonOnce(model, messages, temperature);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            throw new IOException("Upstream returned HTTP " + code + " without body");
        }

        String resp;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            resp = sb.toString();
        }

        if (code < 200 || code >= 300) {
            throw new IOException("Upstream returned HTTP " + code + ": " + resp);
        }

        return resp;
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

    private static String buildBodyJson(String model, List<ChatMessage> messages, Double temperature) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(escapeJson(model)).append("\"");
        sb.append(",\"stream\":true");
        if (temperature != null) {
            sb.append(",\"temperature\":").append(temperature);
        }
        sb.append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            String role = (m == null || m.role() == null || m.role().isBlank()) ? "user" : m.role();
            sb.append("\"role\":\"").append(escapeJson(role)).append("\"");
            Object content = (m == null) ? "" : m.content();
            sb.append(",\"content\":");
            if (content == null) {
                sb.append("\"\"");
            } else if (content instanceof String s) {
                sb.append('"').append(escapeJson(s)).append('"');
            } else {
                try {
                    sb.append(objectMapper.writeValueAsString(content));
                } catch (Exception e) {
                    sb.append('"').append(escapeJson(String.valueOf(content))).append('"');
                }
            }
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static String buildBodyJsonOnce(String model, List<ChatMessage> messages, Double temperature) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(escapeJson(model)).append("\"");
        sb.append(",\"stream\":false");
        if (temperature != null) {
            sb.append(",\"temperature\":").append(temperature);
        }
        sb.append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            String role = (m == null || m.role() == null || m.role().isBlank()) ? "user" : m.role();
            sb.append("\"role\":\"").append(escapeJson(role)).append("\"");
            Object content = (m == null) ? "" : m.content();
            sb.append(",\"content\":");
            if (content == null) {
                sb.append("\"\"");
            } else if (content instanceof String s) {
                sb.append('"').append(escapeJson(s)).append('"');
            } else {
                try {
                    sb.append(objectMapper.writeValueAsString(content));
                } catch (Exception e) {
                    sb.append('"').append(escapeJson(String.valueOf(content))).append('"');
                }
            }
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }
}

