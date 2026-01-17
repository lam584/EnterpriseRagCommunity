package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal OpenAI-compatible embedding client.
 *
 * Upstream:
 * POST {baseUrl}/embeddings
 * Body: {"model":"text-embedding-v4","input":"..."}
 */
@Service
@RequiredArgsConstructor
public class AiEmbeddingService {

    private final AiProperties props;

    public record EmbeddingResult(float[] vector, int dims, String model) {}

    public EmbeddingResult embedOnce(String input, String modelOverride) throws IOException {
        if (input == null) input = "";
        String baseUrl = props.getBaseUrl();
        String apiKey = props.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Missing app.ai.api-key (or env injection)");
        }
        String model = (modelOverride == null || modelOverride.isBlank()) ? "text-embedding-v4" : modelOverride;

        String endpoint = baseUrl;
        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        endpoint = endpoint + "/embeddings";

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(props.getConnectTimeoutMs());
        conn.setReadTimeout(props.getReadTimeoutMs());
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);

        String body = buildBody(model, input);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("Upstream returned HTTP " + code + " without body");

        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) {
            throw new IOException("Embedding upstream error HTTP " + code + ": " + json);
        }

        float[] vec = extractFirstEmbeddingVector(json);
        if (vec == null) {
            throw new IOException("Failed to parse embedding vector from upstream response");
        }
        return new EmbeddingResult(vec, vec.length, model);
    }

    private static String buildBody(String model, String input) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"model\":\"").append(escapeJson(model)).append("\"");
        sb.append(",\"input\":\"").append(escapeJson(input)).append("\"");
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Extremely small JSON extractor:
     * It finds the first "embedding":[...] array under data[0].
     *
     * Not a full JSON parser, but works for common OpenAI-compatible embedding responses.
     */
    static float[] extractFirstEmbeddingVector(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"embedding\"");
        if (idx < 0) return null;
        int arrStart = json.indexOf('[', idx);
        if (arrStart < 0) return null;

        // Find the matching closing ']' for the embedding array.
        int depth = 0;
        int arrEnd = -1;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    arrEnd = i;
                    break;
                }
            }
        }
        if (arrEnd < 0) return null;

        String arr = json.substring(arrStart + 1, arrEnd).trim();
        if (arr.isEmpty()) return new float[0];

        // Count commas to size array (rough and fast).
        int count = 1;
        for (int i = 0; i < arr.length(); i++) if (arr.charAt(i) == ',') count++;

        float[] vec = new float[count];
        int vi = 0;
        int start = 0;
        for (int i = 0; i <= arr.length(); i++) {
            if (i == arr.length() || arr.charAt(i) == ',') {
                String num = arr.substring(start, i).trim();
                if (!num.isEmpty()) {
                    vec[vi++] = Float.parseFloat(num);
                }
                start = i + 1;
            }
        }
        if (vi != vec.length) {
            float[] resized = new float[vi];
            System.arraycopy(vec, 0, resized, 0, vi);
            return resized;
        }
        return vec;
    }
}
