package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

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
    private final AiProvidersConfigService aiProvidersConfigService;
    private final LlmCallQueueService llmCallQueueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record EmbeddingResult(float[] vector, int dims, String model) {}

    public EmbeddingResult embedOnce(String input, String modelOverride) throws IOException {
        return embedOnceForTask(input, modelOverride, null, LlmQueueTaskType.EMBEDDING);
    }

    public EmbeddingResult embedOnce(String input, String modelOverride, String providerId) throws IOException {
        return embedOnceForTask(input, modelOverride, providerId, LlmQueueTaskType.EMBEDDING);
    }

    public EmbeddingResult embedOnceForTask(String input, String modelOverride, String providerId, LlmQueueTaskType taskType) throws IOException {
        if (input == null) input = "";
        AiProvidersConfigService.ResolvedProvider provider = (providerId == null || providerId.isBlank())
                ? aiProvidersConfigService.resolveActiveProvider()
                : aiProvidersConfigService.resolveProvider(providerId.trim());
        String baseUrl = provider == null ? null : provider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = props.getBaseUrl();
        String apiKey = provider == null ? null : provider.apiKey();
        if (apiKey == null || apiKey.isBlank()) apiKey = props.getApiKey();
        Map<String, String> extraHeaders = provider == null ? Map.of() : provider.extraHeaders();
        String model = (modelOverride == null || modelOverride.isBlank())
                ? (provider == null ? null : provider.defaultEmbeddingModel())
                : modelOverride;
        if (model == null || model.isBlank()) model = "text-embedding-v4";

        String providerKey = provider == null ? (providerId == null ? null : providerId.trim()) : provider.id();
        String inputFinal = input;
        String modelFinal = model;
        String baseUrlFinal = baseUrl;
        String apiKeyFinal = apiKey;
        Map<String, String> extraHeadersFinal = extraHeaders;
        Integer connectTimeoutMsFinal = provider == null ? null : provider.connectTimeoutMs();
        Integer readTimeoutMsFinal = provider == null ? null : provider.readTimeoutMs();

        record EmbeddingCallResult(EmbeddingResult result, LlmCallQueueService.UsageMetrics usage) {}

        try {
            String dedupKey = "input_sha256:" + sha256Hex(normalizeForDedup(inputFinal));
            EmbeddingCallResult call = llmCallQueueService.callDedup(
                    taskType == null ? LlmQueueTaskType.EMBEDDING : taskType,
                    providerKey,
                    modelFinal,
                    0,
                    dedupKey,
                    (task) -> {
                        try {
                            task.reportInput("model: " + modelFinal + "\n\ninput:\n" + inputFinal);
                        } catch (Exception ignore) {
                        }
                        String endpoint = baseUrlFinal;
                        if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
                        endpoint = endpoint + "/embeddings";

                        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                        conn.setRequestMethod("POST");
                        Integer connectTimeoutMs = connectTimeoutMsFinal;
                        if (connectTimeoutMs == null || connectTimeoutMs <= 0) connectTimeoutMs = props.getConnectTimeoutMs();
                        Integer readTimeoutMs = readTimeoutMsFinal;
                        if (readTimeoutMs == null || readTimeoutMs <= 0) readTimeoutMs = props.getReadTimeoutMs();
                        conn.setConnectTimeout(connectTimeoutMs);
                        conn.setReadTimeout(readTimeoutMs);
                        conn.setDoOutput(true);
                        conn.setRequestProperty("Content-Type", "application/json");
                        boolean hasAuth = false;
                        if (extraHeadersFinal != null && !extraHeadersFinal.isEmpty()) {
                            for (Map.Entry<String, String> e : extraHeadersFinal.entrySet()) {
                                String k = e.getKey();
                                String v = e.getValue();
                                if (k == null || k.isBlank()) continue;
                                if (v == null) continue;
                                conn.setRequestProperty(k, v);
                                if ("authorization".equals(k.trim().toLowerCase(Locale.ROOT))) hasAuth = true;
                            }
                        }
                        if (!hasAuth && apiKeyFinal != null && !apiKeyFinal.isBlank()) conn.setRequestProperty("Authorization", "Bearer " + apiKeyFinal);

                        String body = buildBody(modelFinal, inputFinal);
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

                        Integer promptTokens = null;
                        Integer totalTokens = null;
                        try {
                            JsonNode root = objectMapper.readTree(json);
                            JsonNode usage = root == null ? null : root.get("usage");
                            if (usage != null && usage.isObject()) {
                                promptTokens = readIntLike(usage.get("prompt_tokens"));
                                totalTokens = readIntLike(usage.get("total_tokens"));
                            }
                        } catch (Exception ignore) {
                            promptTokens = null;
                            totalTokens = null;
                        }
                        if (totalTokens == null) totalTokens = promptTokens;

                        float[] vec = extractFirstEmbeddingVector(json);
                        if (vec == null) {
                            throw new IOException("Failed to parse embedding vector from upstream response");
                        }
                        try {
                            StringBuilder sb = new StringBuilder();
                            sb.append("dims: ").append(vec.length).append('\n');
                            sb.append("preview: ");
                            int take = Math.min(12, vec.length);
                            for (int i = 0; i < take; i++) {
                                if (i > 0) sb.append(", ");
                                sb.append(vec[i]);
                            }
                            if (vec.length > take) sb.append(" ...");
                            task.reportOutput(sb.toString());
                        } catch (Exception ignore) {
                        }
                        LlmCallQueueService.UsageMetrics usageMetrics = (promptTokens == null && totalTokens == null)
                                ? null
                                : new LlmCallQueueService.UsageMetrics(promptTokens, null, totalTokens, null);
                        return new EmbeddingCallResult(new EmbeddingResult(vec, vec.length, modelFinal), usageMetrics);
                    },
                    (r) -> r == null ? null : r.usage()
            );
            return call == null ? null : call.result();
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new IOException("Embedding failed: " + e.getMessage(), e);
        }
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

    private static Integer readIntLike(JsonNode v) {
        if (v == null || v.isNull()) return null;
        try {
            if (v.isInt() || v.isLong()) return v.intValue();
            if (v.isNumber()) return (int) Math.round(v.doubleValue());
            if (v.isTextual()) {
                String t = v.asText().trim();
                if (t.isEmpty()) return null;
                double n = Double.parseDouble(t);
                if (!Double.isFinite(n)) return null;
                return (int) Math.round(n);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
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

    private static String normalizeForDedup(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        return s.replaceAll("\\s+", " ");
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed: " + e.getMessage(), e);
        }
    }
}
