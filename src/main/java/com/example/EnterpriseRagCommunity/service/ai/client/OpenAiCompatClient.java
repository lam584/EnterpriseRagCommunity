package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.service.ai.JsonEscapeSupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenAiCompatClient {

    public interface SseLineConsumer {
        void onLine(String line) throws IOException;
    }

    public record ChatRequest(
            String apiKey,
            String baseUrl,
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            boolean stream
    ) {
    }

    public record ResponsesRequest(
            String apiKey,
            String baseUrl,
            String model,
            Object input,
            Double temperature,
            Integer maxOutputTokens,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) {
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 300_000;

    public OpenAiCompatClient() {
    }

    public void chatCompletionsStream(ChatRequest req, SseLineConsumer consumer) throws IOException {
        String endpoint = buildEndpoint(req.baseUrl(), "/chat/completions");
        HttpURLConnection conn = openJsonPost(endpoint, req, true);

        String model = req.model();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI Model is not configured. Please configure 'app.ai.model' or check your LLM provider settings.");
        }
        List<ChatMessage> messages = req.messages() == null ? List.of() : req.messages();

        String body = buildBodyJson(
                model,
                messages,
                req.temperature(),
                req.topP(),
                req.maxTokens(),
                req.stop(),
                req.enableThinking(),
                req.thinkingBudget(),
                req.extraBody(),
                true
        );
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("Upstream returned HTTP " + code + " without body");

        if (code < 200 || code >= 300) {
            String err = readAll(is);
            throw new IOException("Upstream returned HTTP " + code + ": " + err);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                consumer.onLine(line);
            }
        }
    }

    public String chatCompletionsOnce(ChatRequest req) throws IOException {
        String endpoint = buildEndpoint(req.baseUrl(), "/chat/completions");
        HttpURLConnection conn = openJsonPost(endpoint, req, false);

        String model = req.model();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI Model is not configured. Please configure 'app.ai.model' or check your LLM provider settings.");
        }
        List<ChatMessage> messages = req.messages() == null ? List.of() : req.messages();

        String body = buildBodyJson(
                model,
                messages,
                req.temperature(),
                req.topP(),
                req.maxTokens(),
                req.stop(),
                req.enableThinking(),
                req.thinkingBudget(),
                req.extraBody(),
                false
        );
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

    public String responsesOnce(ResponsesRequest req) throws IOException {
        String endpoint = buildEndpoint(req.baseUrl(), "/responses");
        HttpURLConnection conn = openJsonPost(endpoint, req.apiKey(), req.extraHeaders(), req.connectTimeoutMs(), req.readTimeoutMs(), false);

        String model = req.model();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("AI Model is not configured. Please configure 'app.ai.model' or check your LLM provider settings.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", req.input() == null ? "" : req.input());
        if (req.temperature() != null) {
            body.put("temperature", req.temperature());
        }
        if (req.maxOutputTokens() != null && req.maxOutputTokens() > 0) {
            body.put("max_output_tokens", req.maxOutputTokens());
        }

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

    private HttpURLConnection openJsonPost(String endpoint, ChatRequest req, boolean stream) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(endpoint).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        Integer connectTimeoutMs = req.connectTimeoutMs();
        if (connectTimeoutMs == null || connectTimeoutMs <= 0) connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        Integer readTimeoutMs = req.readTimeoutMs();
        if (readTimeoutMs == null || readTimeoutMs <= 0) readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
        applyHeaders(conn, req.apiKey(), req.extraHeaders());
        return conn;
    }

    private HttpURLConnection openJsonPost(
            String endpoint,
            String apiKey,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            boolean stream
    ) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) java.net.URI.create(endpoint).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        Integer cto = connectTimeoutMs;
        if (cto == null || cto <= 0) cto = DEFAULT_CONNECT_TIMEOUT_MS;
        Integer rto = readTimeoutMs;
        if (rto == null || rto <= 0) rto = DEFAULT_READ_TIMEOUT_MS;
        conn.setConnectTimeout(cto);
        conn.setReadTimeout(rto);

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
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

    private String buildEndpoint(String baseUrl, String path) {
        String endpoint = baseUrl;
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("AI Base URL is not configured. Please configure 'app.ai.base-url' or check your LLM provider settings.");
        }
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return endpoint + path;
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        return JsonEscapeSupport.escapeJson(s);
    }

    private static String buildBodyJson(
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody,
            boolean stream
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(escapeJson(model)).append("\"");
        sb.append(",\"stream\":").append(stream ? "true" : "false");
        if (stream) {
            sb.append(",\"stream_options\":{\"include_usage\":true}");
        }
        if (temperature != null) {
            sb.append(",\"temperature\":").append(temperature);
        }
        if (topP != null) {
            sb.append(",\"top_p\":").append(topP);
        }
        if (maxTokens != null && maxTokens > 0) {
            sb.append(",\"max_tokens\":").append(maxTokens);
        }
        if (stop != null && !stop.isEmpty()) {
            sb.append(",\"stop\":[");
            for (int i = 0; i < stop.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escapeJson(stop.get(i))).append('"');
            }
            sb.append(']');
        }
        if (enableThinking != null) {
            sb.append(",\"enable_thinking\":").append(enableThinking);
        }
        if (thinkingBudget != null && thinkingBudget > 0) {
            sb.append(",\"thinking_budget\":").append(thinkingBudget);
        }
        if (extraBody != null && !extraBody.isEmpty()) {
            for (Map.Entry<String, Object> e : extraBody.entrySet()) {
                String k = e.getKey();
                if (k == null || k.isBlank()) continue;
                String kn = k.trim();
                if (kn.equals("model")
                        || kn.equals("stream")
                        || kn.equals("stream_options")
                        || kn.equals("temperature")
                        || kn.equals("top_p")
                        || kn.equals("max_tokens")
                        || kn.equals("stop")
                        || kn.equals("enable_thinking")
                        || kn.equals("thinking_budget")
                        || kn.equals("messages")) {
                    continue;
                }
                sb.append(",\"").append(escapeJson(kn)).append("\":");
                Object v = e.getValue();
                if (v == null) {
                    sb.append("null");
                } else if (v instanceof String s) {
                    sb.append('"').append(escapeJson(s)).append('"');
                } else if (v instanceof Number || v instanceof Boolean) {
                    sb.append(v);
                } else {
                    try {
                        sb.append(objectMapper.writeValueAsString(v));
                    } catch (Exception ex) {
                        sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
                    }
                }
            }
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
