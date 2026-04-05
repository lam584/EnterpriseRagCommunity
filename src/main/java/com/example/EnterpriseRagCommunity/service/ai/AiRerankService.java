package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.service.ai.client.DashscopeCompatRerankClient;
import com.example.EnterpriseRagCommunity.service.ai.client.DashscopeRerankClient;
import com.example.EnterpriseRagCommunity.service.ai.client.LocalRerankClient;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.client.ResponsesStyleRerankClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiRerankService {

    public record RerankHit(int index, double relevanceScore) {
    }

    public record RerankResult(List<RerankHit> results, Integer totalTokens, String providerId, String model) {
    }

    private final AiProvidersConfigService aiProvidersConfigService;
    private final LlmCallQueueService llmCallQueueService;
    private final PromptsRepository promptsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RerankResult rerankOnce(
            String providerId,
            String modelOverride,
            String query,
            List<String> documents,
            Integer topN,
            String instruct,
            Boolean returnDocuments,
            Double fps
    ) throws IOException {
        AiProvidersConfigService.ResolvedProvider provider = (providerId == null || providerId.isBlank())
                ? aiProvidersConfigService.resolveActiveProvider()
                : aiProvidersConfigService.resolveProvider(providerId.trim());
        String usedProviderId = provider == null ? (providerId == null ? null : providerId.trim()) : provider.id();

        String apiKeyResolved = provider == null ? null : provider.apiKey();
        String baseUrlResolved = provider == null ? null : provider.baseUrl();

        Map<String, String> extraHeadersResolved = provider == null ? Map.of() : provider.extraHeaders();
        Integer connectTimeoutMsResolved = provider == null ? null : provider.connectTimeoutMs();
        Integer readTimeoutMsResolved = provider == null ? null : provider.readTimeoutMs();

        String modelFinal = normalizeModel(modelOverride, provider == null ? null : provider.metadata());
        String queryFinal = query == null ? "" : query;
        List<String> docsFinal = documents == null ? List.of() : documents;
        Integer topNFinal = topN == null ? null : Math.max(1, topN);
        String instructFinal = (instruct == null || instruct.isBlank()) ? null : instruct.trim();
        final String rerankEndpointPathFinal = Objects.requireNonNullElse(
                extractString(provider == null ? null : provider.metadata(), "rerankEndpointPath"),
                "/compatible-api/v1/reranks"
        );

        record RerankCallResult(String rawForParse, String rawUpstream, LlmCallQueueService.UsageMetrics usage) {
        }

        DashscopeRerankClient dashscopeClient = new DashscopeRerankClient();
        DashscopeCompatRerankClient dashscopeCompatClient = new DashscopeCompatRerankClient();
        OpenAiCompatClient openAiCompatClient = new OpenAiCompatClient();
        LocalRerankClient localRerankClient = new LocalRerankClient();
        ResponsesStyleRerankClient responsesStyleRerankClient = new ResponsesStyleRerankClient();
        try {
            RerankCallResult call = llmCallQueueService.call(
                    LlmQueueTaskType.RERANK,
                    usedProviderId,
                    modelFinal,
                    0,
                    (task) -> {
                        try {
                            task.reportInput("model: " + modelFinal + "\nquery: " + queryFinal + "\ndocuments: " + docsFinal.size() + "\ntop_n: " + Objects.toString(topNFinal, "") + "\n");
                        } catch (Exception ignore) {
                        }

                        boolean useDashscope = isDashscopeProvider(usedProviderId, baseUrlResolved);
                        String upstreamRaw = null;
                        LlmCallQueueService.UsageMetrics usage = null;
                        String rawForParse = null;
                        if (useDashscope) {
                            upstreamRaw = dashscopeClient.rerankOnce(new DashscopeRerankClient.RerankRequest(
                                    apiKeyResolved,
                                    baseUrlResolved,
                                    modelFinal,
                                    queryFinal,
                                    docsFinal,
                                    topNFinal,
                                    returnDocuments,
                                    instructFinal,
                                    fps,
                                    extraHeadersResolved,
                                    connectTimeoutMsResolved,
                                    readTimeoutMsResolved
                            ));
                            usage = parseUsageFromJson(upstreamRaw);
                            rawForParse = upstreamRaw;
                        } else {
                            String providerType = provider == null ? "" : (provider.type() == null ? "" : provider.type().trim().toUpperCase(Locale.ROOT));
                            boolean localOpenAiCompat = "LOCAL_OPENAI_COMPAT".equals(providerType);
                            String epLower = rerankEndpointPathFinal.trim().toLowerCase(Locale.ROOT);
                            boolean responsesStyleOk = false;
                            boolean preferResponsesStyleRerank = epLower.contains("/v1/rerank") && !epLower.contains("/reranks");
                            if (preferResponsesStyleRerank) {
                                try {
                                    upstreamRaw = responsesStyleRerankClient.rerankOnce(new ResponsesStyleRerankClient.RerankRequest(
                                            apiKeyResolved,
                                            baseUrlResolved,
                                            rerankEndpointPathFinal,
                                            modelFinal,
                                            queryFinal,
                                            docsFinal,
                                            topNFinal,
                                            returnDocuments,
                                            instructFinal,
                                            extraHeadersResolved,
                                            connectTimeoutMsResolved,
                                            readTimeoutMsResolved
                                    ));
                                    usage = parseUsageFromJson(upstreamRaw);
                                    rawForParse = normalizeRerankJsonFromResponsesResponse(upstreamRaw);
                                    responsesStyleOk = true;
                                } catch (IOException ioe) {
                                    boolean maybeUnsupported = isHttpStatus(ioe, 400, 404, 405, 415, 422);
                                    if (!maybeUnsupported) throw ioe;
                                }
                            }

                            if (!responsesStyleOk) {
                                boolean preferDashscopeCompat = epLower.contains("compatible-api") && epLower.contains("/reranks");
                                boolean dashscopeCompatFailed = false;
                                if (preferDashscopeCompat) {
                                    try {
                                        upstreamRaw = dashscopeCompatClient.rerankOnce(new DashscopeCompatRerankClient.RerankRequest(
                                                apiKeyResolved,
                                                baseUrlResolved,
                                                rerankEndpointPathFinal,
                                                modelFinal,
                                                queryFinal,
                                                docsFinal,
                                                topNFinal,
                                                returnDocuments,
                                                instructFinal,
                                                extraHeadersResolved,
                                                connectTimeoutMsResolved,
                                                readTimeoutMsResolved
                                        ));
                                        usage = parseUsageFromJson(upstreamRaw);
                                        rawForParse = upstreamRaw;
                                    } catch (IOException ioe) {
                                        dashscopeCompatFailed = isHttpStatus(ioe, 400, 404, 405, 415, 422);
                                        if (!dashscopeCompatFailed) throw ioe;
                                        upstreamRaw = null;
                                        usage = null;
                                    }
                                }

                                boolean preferResponses = epLower.contains("/responses");
                                if (!preferDashscopeCompat || dashscopeCompatFailed) {
                                    if (preferResponses) {
                                    upstreamRaw = rerankViaResponsesPromptOnce(
                                            openAiCompatClient,
                                            apiKeyResolved,
                                            resolveResponsesBaseUrl(baseUrlResolved, rerankEndpointPathFinal),
                                            modelFinal,
                                            queryFinal,
                                            docsFinal,
                                            topNFinal,
                                            instructFinal,
                                            extraHeadersResolved,
                                            connectTimeoutMsResolved,
                                            readTimeoutMsResolved
                                    );
                                    usage = llmCallQueueService.parseOpenAiUsageFromJson(upstreamRaw);
                                    rawForParse = normalizeRerankJsonFromResponsesResponse(upstreamRaw);
                                    } else {
                                    try {
                                    upstreamRaw = localRerankClient.rerankOnce(new LocalRerankClient.RerankRequest(
                                            apiKeyResolved,
                                            baseUrlResolved,
                                            rerankEndpointPathFinal,
                                            modelFinal,
                                            queryFinal,
                                            docsFinal,
                                            topNFinal,
                                            extraHeadersResolved,
                                            connectTimeoutMsResolved,
                                            readTimeoutMsResolved
                                    ));
                                    usage = llmCallQueueService.parseOpenAiUsageFromJson(upstreamRaw);
                                    rawForParse = upstreamRaw;
                                } catch (IOException ioe) {
                                    boolean maybeUnsupportedRerankEndpoint = isHttpStatus(ioe, 400, 404, 405, 415, 422);
                                    if (localOpenAiCompat && maybeUnsupportedRerankEndpoint) {
                                        try {
                                            upstreamRaw = rerankViaResponsesPromptOnce(
                                                    openAiCompatClient,
                                                    apiKeyResolved,
                                                    resolveResponsesBaseUrl(baseUrlResolved, rerankEndpointPathFinal),
                                                    modelFinal,
                                                    queryFinal,
                                                    docsFinal,
                                                    topNFinal,
                                                    instructFinal,
                                                    extraHeadersResolved,
                                                    connectTimeoutMsResolved,
                                                    readTimeoutMsResolved
                                            );
                                            usage = llmCallQueueService.parseOpenAiUsageFromJson(upstreamRaw);
                                            rawForParse = normalizeRerankJsonFromResponsesResponse(upstreamRaw);
                                        } catch (Exception ignore) {
                                            upstreamRaw = rerankViaChatPromptOnce(
                                                    openAiCompatClient,
                                                    usedProviderId,
                                                    apiKeyResolved,
                                                    baseUrlResolved,
                                                    modelFinal,
                                                    queryFinal,
                                                    docsFinal,
                                                    topNFinal,
                                                    instructFinal,
                                                    extraHeadersResolved,
                                                    connectTimeoutMsResolved,
                                                    readTimeoutMsResolved
                                            );
                                            usage = llmCallQueueService.parseOpenAiUsageFromJson(upstreamRaw);
                                            rawForParse = normalizeRerankJsonFromChatResponse(upstreamRaw);
                                        }
                                    } else if (!localOpenAiCompat) {
                                        upstreamRaw = rerankViaChatPromptOnce(
                                                openAiCompatClient,
                                                usedProviderId,
                                                apiKeyResolved,
                                                baseUrlResolved,
                                                modelFinal,
                                                queryFinal,
                                                docsFinal,
                                                topNFinal,
                                                instructFinal,
                                                extraHeadersResolved,
                                                connectTimeoutMsResolved,
                                                readTimeoutMsResolved
                                        );
                                        usage = llmCallQueueService.parseOpenAiUsageFromJson(upstreamRaw);
                                        rawForParse = normalizeRerankJsonFromChatResponse(upstreamRaw);
                                    } else {
                                        throw ioe;
                                    }
                                }
                                    }
                                }
                            }
                        }

                        try {
                            task.reportOutput(upstreamRaw == null ? "" : upstreamRaw);
                        } catch (Exception ignore) {
                        }
                        return new RerankCallResult(rawForParse, upstreamRaw, usage);
                    },
                    (r) -> r == null ? null : r.usage()
            );

            String raw = call == null ? null : call.rawForParse();
            List<RerankHit> hits = new ArrayList<>(parseResults(raw));
            hits.sort(Comparator.comparingDouble(RerankHit::relevanceScore).reversed());
            Integer totalTokens = call == null || call.usage() == null ? null : call.usage().totalTokens();
            return new RerankResult(hits, totalTokens, usedProviderId, modelFinal);
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();
            throw new IOException("Rerank failed: " + msg, e);
        }
    }

    private static boolean isDashscopeProvider(String providerId, String baseUrl) {
        String pid = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        if (pid.equals("aliyun") || pid.equals("dashscope")) return true;
        String u = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
        return u.contains("dashscope.aliyuncs.com");
    }

    private String getRerankSystemPrompt() {
        return promptsRepository.findByPromptCode("RERANK_DEFAULT")
                .map(com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity::getSystemPrompt)
                .orElseThrow(() -> new IllegalStateException("Prompt RERANK_DEFAULT not found"));
    }

    private String rerankViaChatPromptOnce(
            OpenAiCompatClient openAiCompatClient,
            String providerId,
            String apiKey,
            String baseUrl,
            String model,
            String query,
            List<String> documents,
            Integer topN,
            String instruct,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) throws IOException {
        String sys = getRerankSystemPrompt();
        if (instruct != null && !instruct.isBlank()) {
            sys = sys + "\n附加说明（可选）：\n" + instruct.trim() + "\n";
        }

        String user = buildRerankUserPrompt(query, documents, topN);

        List<ChatMessage> messages = List.of(
                ChatMessage.system(sys),
                ChatMessage.user(user)
        );
        return openAiCompatClient.chatCompletionsOnce(new OpenAiCompatClient.ChatRequest(
                apiKey,
                normalizeOpenAiCompatBaseUrl(baseUrl),
                model,
                messages,
                0.0,
                null,
                1024,
                null,
                false,
                null,
                null,
                extraHeaders,
                connectTimeoutMs,
                readTimeoutMs,
                false
        ));
    }

    private String rerankViaResponsesPromptOnce(
            OpenAiCompatClient openAiCompatClient,
            String apiKey,
            String baseUrlWithV1,
            String model,
            String query,
            List<String> documents,
            Integer topN,
            String instruct,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) throws IOException {
        String sys = getRerankSystemPrompt();
        if (instruct != null && !instruct.isBlank()) {
            sys = sys + "\n附加说明（可选）：\n" + instruct.trim() + "\n";
        }

        String user = buildRerankUserPrompt(query, documents, topN);
        List<Map<String, Object>> input = List.of(
                Map.of("role", "system", "content", sys),
                Map.of("role", "user", "content", user)
        );
        return openAiCompatClient.responsesOnce(new OpenAiCompatClient.ResponsesRequest(
                apiKey,
                baseUrlWithV1,
                model,
                input,
                0.0,
                1024,
                extraHeaders,
                connectTimeoutMs,
                readTimeoutMs
        ));
    }

    private static String normalizeOpenAiCompatBaseUrl(String baseUrl) {
        String u = baseUrl == null ? "" : baseUrl.trim();
        if (u.isBlank()) return u;
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/v1") || lower.contains("/v1/")) return u;
        if (lower.endsWith("/api/v1") || lower.contains("/api/v1/")) return u;
        return u + "/v1";
    }

    private static String resolveResponsesBaseUrl(String baseUrl, String rerankEndpointPath) {
        String ep = rerankEndpointPath == null ? "" : rerankEndpointPath.trim();
        if (ep.startsWith("http://") || ep.startsWith("https://")) {
            String lower = ep.toLowerCase(Locale.ROOT);
            int idx = lower.lastIndexOf("/responses");
            if (idx > 0) {
                String b = ep.substring(0, idx);
                if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
                return b;
            }
            if (ep.endsWith("/")) ep = ep.substring(0, ep.length() - 1);
            return ep;
        }
        return normalizeOpenAiCompatBaseUrl(baseUrl);
    }

    private static String extractString(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) return null;
        Object v = metadata.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private static boolean isHttpStatus(IOException e, int... codes) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage();
        for (int c : codes) {
            if (msg.contains("HTTP " + c)) return true;
        }
        return false;
    }

    private static String buildRerankUserPrompt(String query, List<String> documents, Integer topN) {
        String q = query == null ? "" : query;
        List<String> docs = documents == null ? List.of() : documents;
        int n = topN == null ? 0 : Math.max(0, topN);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"query\": ").append(objectMapperSafeString(q)).append(",\n");
        if (n > 0) sb.append("  \"topN\": ").append(n).append(",\n");
        sb.append("  \"documents\": [\n");
        for (int i = 0; i < docs.size(); i++) {
            String d = docs.get(i);
            if (d == null) d = "";
            sb.append("    ").append(objectMapperSafeString(d));
            if (i + 1 < docs.size()) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String objectMapperSafeString(String s) {
        String t = s == null ? "" : s;
        StringBuilder out = new StringBuilder(t.length() + 2);
        out.append('"');
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private String normalizeRerankJsonFromChatResponse(String rawJson) throws IOException {
        return AiResponseParsingUtils.normalizeJsonPayload(extractAssistantContent(rawJson));
    }

    String normalizeRerankJsonFromResponsesResponse(String rawJson) throws IOException {
        return AiResponseParsingUtils.normalizeJsonPayload(extractResponsesOutputText(rawJson));
    }

    private static String stripCodeFences(String text) {
        return AiResponseParsingUtils.stripCodeFences(text);
    }

    private static String shrink(String text, int maxChars) {
        return AiResponseParsingUtils.shrink(text, maxChars);
    }

    private String extractAssistantContent(String rawJson) {
        if (rawJson == null) return null;
        String s = rawJson.trim();
        if (s.isEmpty()) return s;
        try {
            JsonNode root = objectMapper.readTree(s);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (contentNode.isTextual()) return contentNode.asText();
                JsonNode textNode = first.path("text");
                if (textNode.isTextual()) return textNode.asText();
            }
            JsonNode output = root.get("output");
            if (output != null) {
                if (output.isTextual()) return output.asText();
                if (output.isObject()) {
                    JsonNode text = output.get("text");
                    if (text != null && text.isTextual()) return text.asText();
                    JsonNode content = output.get("content");
                    if (content != null && content.isTextual()) return content.asText();
                    String messageText = extractMessageText(output.get("message"));
                    if (messageText != null) return messageText;
                }
            }
            JsonNode response = root.get("response");
            if (response != null && response.isTextual()) return response.asText();
            JsonNode result = root.get("result");
            if (result != null && result.isTextual()) return result.asText();
            String messageText = extractMessageText(root.get("message"));
            if (messageText != null) return messageText;
        } catch (Exception ignore) {
        }
        return s;
    }

    private static String extractMessageText(JsonNode messageNode) {
        if (messageNode == null || messageNode.isNull()) return null;
        if (messageNode.isTextual()) return messageNode.asText();
        if (messageNode.isObject()) {
            JsonNode content = messageNode.get("content");
            if (content != null && content.isTextual()) return content.asText();
        }
        return null;
    }

    private String extractResponsesOutputText(String rawJson) {
        return extractResponsesOutputText(objectMapper, rawJson);
    }

    static String extractResponsesOutputText(ObjectMapper objectMapper, String rawJson) {
        if (rawJson == null) return null;
        String s = rawJson.trim();
        if (s.isEmpty()) return s;
        try {
            JsonNode root = objectMapper.readTree(s);
            JsonNode outputText = root.get("output_text");
            if (outputText != null && outputText.isTextual()) return outputText.asText();

            JsonNode output = root.get("output");
            if (output != null && output.isArray()) {
                for (JsonNode item : output) {
                    if (item == null || item.isNull()) continue;
                    JsonNode content = item.get("content");
                    if (content != null && content.isArray()) {
                        for (JsonNode c : content) {
                            if (c == null || c.isNull()) continue;
                            JsonNode text = c.get("text");
                            if (text != null && text.isTextual()) return text.asText();
                            if (c.isTextual()) return c.asText();
                        }
                    }
                    JsonNode text = item.get("text");
                    if (text != null && text.isTextual()) return text.asText();
                }
            }

            JsonNode response = root.get("response");
            if (response != null && response.isTextual()) return response.asText();
            JsonNode result = root.get("result");
            if (result != null && result.isTextual()) return result.asText();
        } catch (Exception ignore) {
        }
        return s;
    }

    static String normalizeModel(String modelOverride, Map<String, Object> providerMetadata) {
        String m = (modelOverride == null || modelOverride.isBlank()) ? null : modelOverride.trim();
        if (m != null) return m;
        if (providerMetadata != null) {
            Object v = providerMetadata.get("defaultRerankModel");
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isBlank()) return s;
            }
        }
        return "qwen3-rerank";
    }

    List<RerankHit> parseResults(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode arr = root.path("output").path("results");
            if (!arr.isArray()) arr = root.path("results");
            if (!arr.isArray()) arr = root.path("data");
            if (!arr.isArray()) arr = root.path("rankings");
            if (!arr.isArray()) arr = root.path("ranks");
            if (!arr.isArray() && root.isArray()) arr = root;
            if (!arr.isArray()) return List.of();

            List<RerankHit> out = new ArrayList<>();
            int pos = 0;
            for (JsonNode n : arr) {
                if (n == null || n.isNull()) continue;
                Integer idxObj = readIntLike(n.get("index"));
                if (idxObj == null) idxObj = readIntLike(n.get("doc_index"));
                if (idxObj == null) idxObj = readIntLike(n.get("document_index"));
                if (idxObj == null) idxObj = readIntLike(n.get("documentIndex"));
                if (idxObj == null) idxObj = readIntLike(n.get("idx"));
                if (idxObj == null) idxObj = readIntLike(n.get("i"));
                if (idxObj == null) idxObj = readIntLike(n.get("position"));
                if (idxObj == null) idxObj = readIntLike(n.get("id"));
                int idx = idxObj == null ? pos : idxObj;
                Double score = readDoubleLike(n.get("relevance_score"));
                if (score == null) score = readDoubleLike(n.get("score"));
                if (score == null) score = readDoubleLike(n.get("relevanceScore"));
                if (score == null) score = readDoubleLike(n.get("relevance"));
                if (score == null) score = readDoubleLike(n.get("confidence"));
                if (score == null) score = 0.0;
                if (score > 1.0 && score <= 100.0) score = score / 100.0;
                out.add(new RerankHit(idx, score));
                pos++;
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    LlmCallQueueService.UsageMetrics parseUsageFromJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) usage = root.path("output").path("usage");
            if (usage == null || !usage.isObject()) return null;
            Integer total = readIntLike(usage.get("total_tokens"));
            if (total == null) total = readIntLike(usage.get("totalTokens"));
            if (total == null) return null;
            return new LlmCallQueueService.UsageMetrics(total, null, total, null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer readIntLike(JsonNode v) {
        return AiResponseParsingUtils.readIntLike(v);
    }

    private static Double readDoubleLike(JsonNode v) {
        if (v == null || v.isNull()) return null;
        try {
            if (v.isNumber()) {
                double d = v.doubleValue();
                return Double.isFinite(d) ? d : null;
            }
            if (v.isTextual()) {
                String t = v.asText().trim();
                if (t.isEmpty()) return null;
                double d = Double.parseDouble(t);
                return Double.isFinite(d) ? d : null;
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }
}
