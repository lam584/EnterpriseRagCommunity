package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.service.ai.AiResponseParsingUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.EnterpriseRagCommunity.service.ai.ChatMessageSupport;
import org.springframework.stereotype.Component;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAnchorSnippetSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationTagSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationViolationSnippetSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

record StageCallResult(
        String decisionSuggestion,
        Double riskScore,
        List<String> labels,
        String decision,
        Double score,
        List<String> reasons,
        List<String> riskTags,
        String severity,
        Double uncertainty,
        List<String> evidence,
        String rawModelOutput,
        String model,
        Long latencyMs,
        LlmModerationTestResponse.Usage usage,
        List<LlmModerationTestResponse.Message> promptMessages,
        String description,
        String inputMode
) {}

final class ParsedDecision {
    String decisionSuggestion;
    Double riskScore;
    List<String> labels;
    String decision;
    Double score;
    List<String> reasons;
    List<String> riskTags;
    String description;
    String severity;
    Double uncertainty;
    List<String> evidence;
}

@Component
@RequiredArgsConstructor
class AdminModerationLlmUpstreamSupport {

    private final LlmGateway llmGateway;
    private final AdminModerationLlmImageSupport imageSupport;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern IMAGE_PLACEHOLDER = Pattern.compile("\\[\\[IMAGE_(\\d+)]]");

    StageCallResult callTextOnce(
            String systemPrompt,
            String userPrompt,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String providerId,
            String modelOverride,
            Boolean enableThinking,
            boolean useQueue
    ) {
        List<ChatMessage> messages = ChatMessageSupport.buildSystemUserMessages(systemPrompt, userPrompt);
        return callOnce(LlmQueueTaskType.MULTIMODAL_MODERATION, providerId, modelOverride, messages, temperature, topP, maxTokens, enableThinking, null, "multimodal", useQueue, null);
    }

    StageCallResult callImageDescribeOnce(
            String systemPrompt,
            PromptVars vars,
            List<ImageRef> images,
            String promptTemplate,
            String inputJson,
            Double temperature,
            Double topP,
            Integer maxTokens,
            String providerId,
            String modelOverride,
            Boolean enableThinking,
            Integer imageTokenBudget,
            Integer maxImagesPerRequest,
            Boolean highResolutionImages,
            Integer maxPixels,
            boolean useQueue
    ) {
        String instruction0 = renderVisionPrompt(promptTemplate, vars);
        String instruction = mergePromptAndJson(promptTemplate, instruction0, inputJson);

        // Determine effective model name for DashScope image upload.
        // Local providers (Ollama, LLM-Studio) don't need cloud upload — skip by passing null.
        String effectiveModelForUpload = modelOverride;
        try {
            var provider = llmGateway.resolve(providerId);
            if (provider != null && "LOCAL_OPENAI_COMPAT".equalsIgnoreCase(provider.type())) {
                effectiveModelForUpload = null;
            }
        } catch (Exception ignored) {
        }

        List<VisionImageInput> inputs = new ArrayList<>();
        if (images != null) {
            for (ImageRef img : images) {
                if (img == null) continue;
                String u = imageSupport.encodeImageUrlForUpstream(img, effectiveModelForUpload);
                if (u == null || u.isBlank()) continue;
                int tokens = imageSupport.estimateVisionImageTokens(img, modelOverride, highResolutionImages, maxPixels);
                inputs.add(new VisionImageInput(img, u.trim(), tokens));
            }
        }
        if (inputs.isEmpty()) {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("type", "text", "text", instruction));
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.userParts(parts));
            return callOnce(LlmQueueTaskType.MULTIMODAL_MODERATION, providerId, modelOverride, messages, temperature, topP, maxTokens, enableThinking, null, "multimodal", useQueue, null);
        }

        int maxImgs = (maxImagesPerRequest == null || maxImagesPerRequest < 1) ? Integer.MAX_VALUE : maxImagesPerRequest;
        Integer budget = (imageTokenBudget == null || imageTokenBudget < 1) ? null : imageTokenBudget;

        List<List<VisionImageInput>> batches = new ArrayList<>();
        if (budget == null && maxImgs == Integer.MAX_VALUE) {
            batches.add(inputs);
        } else {
            List<VisionImageInput> cur = new ArrayList<>();
            long curTokens = 0L;
            for (VisionImageInput in : inputs) {
                int t = Math.max(1, in.tokens());
                boolean hitCount = cur.size() >= maxImgs;
                boolean hitBudget = budget != null && !cur.isEmpty() && (curTokens + t) > budget;
                if (hitCount || hitBudget) {
                    batches.add(cur);
                    cur = new ArrayList<>();
                    curTokens = 0L;
                }
                cur.add(in);
                curTokens += t;
            }
            batches.add(cur);
        }

        boolean sendMaxPixels = !Boolean.TRUE.equals(highResolutionImages) && maxPixels != null && maxPixels > 0;
        Map<String, Object> extraBody = Boolean.TRUE.equals(highResolutionImages)
                ? Map.of("vl_high_resolution_images", true)
                : null;

        boolean hasOssUrl = inputs.stream().anyMatch(in -> in.upstreamUrl() != null && in.upstreamUrl().startsWith("oss://"));
        Map<String, String> extraHeaders = hasOssUrl ? Map.of("X-DashScope-OssResourceResolve", "enable") : null;

        long latencySum = 0L;
        int promptTokensSum = 0;
        int completionTokensSum = 0;
        int totalTokensSum = 0;
        Double maxScore = null;
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        LinkedHashSet<String> riskTags = new LinkedHashSet<>();
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        LinkedHashSet<String> evidence = new LinkedHashSet<>();
        Double maxUncertainty = null;
        String maxSeverity = null;
        StringBuilder desc = new StringBuilder();
        String lastRaw = null;
        String usedModel = null;
        String decision = "APPROVE";
        List<LlmModerationTestResponse.Message> lastPromptMessages = null;

        for (int i = 0; i < batches.size(); i++) {
            List<Map<String, Object>> parts = new ArrayList<>();
            StringBuilder instr = new StringBuilder(instruction);
            instr.append("\n\n[IMAGES]\n");
            int imgIdx = 0;
            for (VisionImageInput in : batches.get(i)) {
                imgIdx += 1;
                instr.append("[[IMAGE_").append(imgIdx).append("]]: ").append(in.upstreamUrl()).append('\n');
                LinkedHashMap<String, Object> part = new LinkedHashMap<>();
                part.put("type", "image_url");
                part.put("image_url", Map.of("url", in.upstreamUrl()));
                if (sendMaxPixels) part.put("max_pixels", maxPixels);
                parts.add(part);
            }
            parts.addFirst(Map.of("type", "text", "text", instr.toString().trim()));
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(systemPrompt));
            messages.add(ChatMessage.userParts(parts));
            StageCallResult r = callOnce(LlmQueueTaskType.MULTIMODAL_MODERATION, providerId, modelOverride, messages, temperature, topP, maxTokens, enableThinking, extraBody, "multimodal", useQueue, extraHeaders);
            if (r == null) {
                return new StageCallResult(
                        "ESCALATE",
                        null,
                        List.of(),
                        "HUMAN",
                        null,
                        List.of("上游AI调用失败: empty response"),
                        List.of("UPSTREAM_ERROR"),
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        0L,
                        null,
                        null,
                        null,
                        "multimodal"
                );
            }
            lastPromptMessages = r.promptMessages();
            if ("HUMAN".equalsIgnoreCase(r.decision())) return r;

            latencySum += r.latencyMs() == null ? 0L : Math.max(0L, r.latencyMs());
            if (r.usage() != null) {
                if (r.usage().getPromptTokens() != null) promptTokensSum += Math.max(0, r.usage().getPromptTokens());
                if (r.usage().getCompletionTokens() != null) completionTokensSum += Math.max(0, r.usage().getCompletionTokens());
                if (r.usage().getTotalTokens() != null) totalTokensSum += Math.max(0, r.usage().getTotalTokens());
            }
            if (r.score() != null && Double.isFinite(r.score())) {
                if (maxScore == null || r.score() > maxScore) maxScore = r.score();
            }
            if (r.labels() != null) {
                for (String t : r.labels()) {
                    if (t != null && !t.isBlank()) labels.add(t);
                }
            }
            if (r.riskTags() != null) {
                for (String t : r.riskTags()) {
                    if (t != null && !t.isBlank()) riskTags.add(t);
                }
            }
            if (r.reasons() != null) {
                for (String s : r.reasons()) {
                    if (s != null && !s.isBlank()) reasons.add(s);
                }
            }
            if (r.evidence() != null) {
                for (String ev : r.evidence()) {
                    if (ev != null && !ev.isBlank()) evidence.add(ev);
                }
            }
            if (r.uncertainty() != null && Double.isFinite(r.uncertainty())) {
                if (maxUncertainty == null || r.uncertainty() > maxUncertainty) maxUncertainty = r.uncertainty();
            }
            if (r.severity() != null && !r.severity().isBlank()) {
                maxSeverity = maxSeverity(maxSeverity, r.severity());
            }
            if (r.description() != null && !r.description().isBlank()) {
                if (!desc.isEmpty()) desc.append("\n\n");
                desc.append("[BATCH ").append(i + 1).append('/').append(batches.size()).append("]\n");
                desc.append(r.description().trim());
            }
            if (r.rawModelOutput() != null && !r.rawModelOutput().isBlank()) lastRaw = r.rawModelOutput();
            if (usedModel == null && r.model() != null && !r.model().isBlank()) usedModel = r.model();
            if ("REJECT".equalsIgnoreCase(r.decision())) decision = "REJECT";
        }

        LlmModerationTestResponse.Usage usage = new LlmModerationTestResponse.Usage();
        usage.setPromptTokens(promptTokensSum == 0 ? null : promptTokensSum);
        usage.setCompletionTokens(completionTokensSum == 0 ? null : completionTokensSum);
        usage.setTotalTokens(totalTokensSum == 0 ? null : totalTokensSum);

        return new StageCallResult(
                "REJECT".equalsIgnoreCase(decision) ? "REJECT" : "ALLOW",
                maxScore,
                labels.isEmpty() ? List.of() : new ArrayList<>(labels),
                decision,
                maxScore,
                reasons.isEmpty() ? List.of() : new ArrayList<>(reasons),
                riskTags.isEmpty() ? List.of() : new ArrayList<>(riskTags),
                maxSeverity,
                maxUncertainty,
                evidence.isEmpty() ? List.of() : new ArrayList<>(evidence),
                lastRaw,
                usedModel,
                latencySum,
                usage,
                lastPromptMessages,
                desc.isEmpty() ? null : desc.toString(),
                "multimodal"
        );
    }

    static String renderVisionPrompt(String promptTemplate, PromptVars vars) {
        String tpl = promptTemplate == null ? "" : promptTemplate;
        String title = vars == null ? "" : nullToEmpty(vars.title()).trim();
        String content = vars == null ? "" : nullToEmpty(vars.content()).trim();
        String t = vars == null ? "" : nullToEmpty(vars.text()).trim();
        if (t.length() > 1000) t = t.substring(0, 1000);
        return tpl
                .replace("{{text}}", t)
                .replace("{{title}}", title)
                .replace("{{content}}", content);
    }

    static String renderTextPrompt(String promptTemplate, PromptVars vars) {
        String tpl = promptTemplate == null ? "" : promptTemplate;
        String title = vars == null ? "" : nullToEmpty(vars.title());
        String content = vars == null ? "" : nullToEmpty(vars.content());
        String text = vars == null ? "" : nullToEmpty(vars.text());
        return tpl
                .replace("{{text}}", text)
                .replace("{{title}}", title)
                .replace("{{content}}", content);
    }

    static String renderJudgePrompt(
            String tpl,
            String originalText,
            String imageDescription,
            double textScore,
            double imageScore,
            List<String> textReasons,
            List<String> imageReasons
    ) {
        String t = trimToMax(originalText, 3000);
        String desc = trimToMax(imageDescription, 2000);
        String tr = joinWithCnSemicolon(textReasons);
        String ir = joinWithCnSemicolon(imageReasons);
        return (tpl == null ? "" : tpl)
                .replace("{{text}}", t)
                .replace("{{imageDescription}}", desc)
                .replace("{{textScore}}", String.valueOf(textScore))
                .replace("{{imageScore}}", String.valueOf(imageScore))
                .replace("{{textReasons}}", tr)
                .replace("{{imageReasons}}", ir);
    }

    static String renderJudgeUpgradePrompt(
            String tpl,
            String originalText,
            String imageDescription,
            double textScore,
            double imageScore,
            double judgeScore,
            double judgeThreshold,
            List<String> textReasons,
            List<String> imageReasons,
            List<String> judgeReasons,
            List<String> textEvidence,
            List<String> imageEvidence,
            List<String> judgeEvidence
    ) {
        String t = trimToMax(originalText, 3000);
        String desc = trimToMax(imageDescription, 2000);
        String tr = joinWithCnSemicolon(textReasons);
        String ir = joinWithCnSemicolon(imageReasons);
        String cr = joinWithCnSemicolon(judgeReasons);
        String tev = trimToMax(joinWithNewline(textEvidence), 1200);
        String iev = trimToMax(joinWithNewline(imageEvidence), 1200);
        String cev = trimToMax(joinWithNewline(judgeEvidence), 1200);
        return (tpl == null ? "" : tpl)
                .replace("{{text}}", t)
                .replace("{{imageDescription}}", desc)
                .replace("{{textScore}}", String.valueOf(textScore))
                .replace("{{imageScore}}", String.valueOf(imageScore))
                .replace("{{judgeScore}}", String.valueOf(judgeScore))
                .replace("{{judgeThreshold}}", String.valueOf(judgeThreshold))
                .replace("{{textReasons}}", tr)
                .replace("{{imageReasons}}", ir)
                .replace("{{judgeReasons}}", cr)
                .replace("{{textEvidence}}", tev)
                .replace("{{imageEvidence}}", iev)
                .replace("{{judgeEvidence}}", cev);
    }

    private static String trimToMax(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() > maxLength) return text.substring(0, maxLength);
        return text;
    }

    private static String joinWithCnSemicolon(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("；", values);
    }

    private static String joinWithNewline(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join("\n", values);
    }

    static String mergePromptAndJson(String promptTemplate, String renderedPrompt, String inputJson) {
        String json = inputJson == null ? null : inputJson.trim();
        if (json == null || json.isBlank()) return renderedPrompt;
        String out = renderedPrompt == null ? "" : renderedPrompt;
        if (out.contains("{{json}}")) {
            return out.replace("{{json}}", json);
        }
        return (out + "\n\n" + json).trim();
    }

    StageCallResult callOnce(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Boolean enableThinking,
            Map<String, Object> extraBody,
            String inputMode,
            boolean useQueue,
            Map<String, String> extraRequestHeaders
    ) {
        long started = System.currentTimeMillis();
        try {
            LlmGateway.RoutedChatOnceResult routed;
            if (extraRequestHeaders != null && !extraRequestHeaders.isEmpty()) {
                routed = useQueue
                        ? llmGateway.chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, null, enableThinking, null, extraBody, extraRequestHeaders)
                        : llmGateway.chatOnceRoutedNoQueue(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, null, enableThinking, null, extraBody, extraRequestHeaders);
            } else {
                routed = useQueue
                        ? llmGateway.chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, null, enableThinking, null, extraBody)
                        : llmGateway.chatOnceRoutedNoQueue(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, null, enableThinking, null, extraBody);
            }
            long latency = System.currentTimeMillis() - started;
            String rawJson = routed == null ? null : routed.text();
            String assistantText = extractAssistantContent(rawJson);
            ParsedDecision parsed = parseDecisionFromAssistantText(assistantText);
            String userText = extractUserTextForEvidence(messages);

            if (parsed != null && parsed.evidence != null && !parsed.evidence.isEmpty()) {
                parsed.evidence = enrichEvidenceWithText(parsed.evidence, userText);
            }

            if (parsed != null && "REJECT".equalsIgnoreCase(parsed.decisionSuggestion)) {
                if ((parsed.evidence == null || parsed.evidence.isEmpty()) && taskType == LlmQueueTaskType.MULTIMODAL_MODERATION) {
                    List<String> ph = extractImagePlaceholders(userText);
                    if (!ph.isEmpty()) {
                        parsed.evidence = List.of(String.join(" ", ph));
                    } else {
                        String fallbackEvidence = deriveFallbackEvidenceFromUserText(userText);
                        if (fallbackEvidence != null && !fallbackEvidence.isBlank()) {
                            parsed.evidence = List.of(fallbackEvidence);
                        }
                    }
                }
                if (parsed.evidence == null || parsed.evidence.isEmpty()) {
                    parsed.decisionSuggestion = "ESCALATE";
                    parsed.decision = suggestionToDecision(parsed.decisionSuggestion, parsed.score);
                    if (parsed.reasons == null) parsed.reasons = new ArrayList<>();
                    parsed.reasons.add("REJECT decision downgraded to ESCALATE due to missing evidence");
                } else if (taskType == LlmQueueTaskType.MULTIMODAL_MODERATION) {
                    if (!hasVerifiableEvidence(userText, parsed.evidence)) {
                        parsed.decisionSuggestion = "ESCALATE";
                        parsed.decision = suggestionToDecision(parsed.decisionSuggestion, parsed.score);
                        if (parsed.reasons == null) parsed.reasons = new ArrayList<>();
                        parsed.reasons.add("REJECT decision downgraded to ESCALATE due to unverifiable evidence");
                    }
                }
            }

            LlmModerationTestResponse.Usage usage = null;
            if (routed != null && routed.usage() != null) {
                usage = new LlmModerationTestResponse.Usage();
                usage.setPromptTokens(routed.usage().promptTokens());
                usage.setCompletionTokens(routed.usage().completionTokens());
                usage.setTotalTokens(routed.usage().totalTokens());
            }
            List<LlmModerationTestResponse.Message> promptMessages = new ArrayList<>();
            if (messages != null) {
                for (ChatMessage m : messages) {
                    if (m == null) continue;
                    String role = m.role();
                    if (role == null || role.isBlank()) continue;
                    Object c = m.content();
                    String content = switch (c) {
                        case null -> "";
                        case String s -> s;
                        case List<?> list -> partsToDebugText(list);
                        default -> "[non_text_content]";
                    };
                    LlmModerationTestResponse.Message pm = new LlmModerationTestResponse.Message();
                    pm.setRole(role);
                    pm.setContent(content);
                    promptMessages.add(pm);
                }
            }
            return new StageCallResult(
                    parsed == null ? "ESCALATE" : parsed.decisionSuggestion,
                    parsed == null ? null : parsed.riskScore,
                    parsed == null ? List.of() : parsed.labels,
                    parsed == null ? "HUMAN" : parsed.decision,
                    parsed == null ? null : parsed.score,
                    parsed == null ? List.of("Model output could not be parsed as JSON") : parsed.reasons,
                    parsed == null ? List.of("PARSE_ERROR") : parsed.riskTags,
                    parsed == null ? null : parsed.severity,
                    parsed == null ? null : parsed.uncertainty,
                    parsed == null ? null : parsed.evidence,
                    assistantText,
                    routed == null ? null : routed.model(),
                    latency,
                    usage,
                    promptMessages.isEmpty() ? null : promptMessages,
                    parsed == null ? null : parsed.description,
                    inputMode
            );
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - started;
            ProviderBlockedInfo blocked = detectProviderOutputBlocked(e);
            if (blocked != null) {
                List<String> rs = new ArrayList<>();
                rs.add(blocked.reason());
                if (blocked.requestId() != null && !blocked.requestId().isBlank()) {
                    rs.add("upstream_request_id=" + blocked.requestId());
                }
                return new StageCallResult(
                        "ESCALATE",
                        null,
                        List.of(),
                        "HUMAN",
                        null,
                        rs,
                        List.of("PROVIDER_OUTPUT_BLOCKED"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        latency,
                        null,
                        null,
                        null,
                        inputMode
                );
            }
            return new StageCallResult(
                    "ESCALATE",
                    null,
                    List.of(),
                    "HUMAN",
                    null,
                    List.of("上游AI调用失败: " + e.getMessage()),
                    List.of("UPSTREAM_ERROR"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    latency,
                    null,
                    null,
                    null,
                    inputMode
            );
        }
    }

    private record ProviderBlockedInfo(String reason, String requestId) {
    }

    private ProviderBlockedInfo detectProviderOutputBlocked(Exception e) {
        if (e == null) return null;
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return null;
        String t = msg.toLowerCase(Locale.ROOT);
        if (!t.contains("data_inspection_failed") && !t.contains("inappropriate content") && !t.contains("inappropriate-content")) {
            return null;
        }

        String json = extractJsonObjectFromText(msg);
        if (json == null) {
            return new ProviderBlockedInfo("上游输出安全审查拦截，无法获取模型结构化结果", null);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode err = root.path("error");
            String code = textOrNull(err.path("code"));
            String message = textOrNull(err.path("message"));
            if (code == null || code.isBlank()) {
                code = textOrNull(err.path("type"));
            }
            boolean matched = code != null && code.trim().equalsIgnoreCase("data_inspection_failed");
            if (!matched && message != null) {
                String m = message.toLowerCase(Locale.ROOT);
                if (m.contains("inappropriate content") || m.contains("inappropriate-content")) matched = true;
            }
            if (!matched) return null;
            String requestId = textOrNull(root.path("request_id"));
            if (requestId == null || requestId.isBlank()) requestId = textOrNull(root.path("id"));
            String reason = "上游输出安全审查拦截，无法获取模型结构化结果";
            if (code != null && !code.isBlank()) reason = reason + "（" + code.trim() + "）";
            return new ProviderBlockedInfo(reason, requestId);
        } catch (Exception ignore) {
            return new ProviderBlockedInfo("上游输出安全审查拦截，无法获取模型结构化结果", null);
        }
    }

    private static String extractJsonObjectFromText(String text) {
        if (text == null || text.isBlank()) return null;
        int l = text.indexOf('{');
        int r = text.lastIndexOf('}');
        if (l < 0 || r <= l) return null;
        return text.substring(l, r + 1);
    }

    private static String extractUserTextForEvidence(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            if (m == null) continue;
            if (!"user".equalsIgnoreCase(m.role())) continue;
            Object c = m.content();
            switch (c) {
                case String s -> {
                    sb.append(s);
                    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                }
                case List<?> parts -> {
                    for (Object it : parts) {
                        if (!(it instanceof Map<?, ?> p)) continue;
                        Object tv = p.get("type");
                        String type = tv == null ? null : tv.toString();
                        if (!"text".equals(type)) continue;
                        Object text = p.get("text");
                        if (text != null) sb.append(text);
                        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                    }
                }
                case null, default -> {
                }
            }
        }
        return sb.toString();
    }

    private static String deriveFallbackEvidenceFromUserText(String userText) {
        if (userText == null || userText.isBlank()) return null;

        String fromContentField = firstMeaningfulLine(afterMarker(userText, "内容:"));
        if (fromContentField != null) return clipEvidence(fromContentField);

        String textSection = afterMarker(userText, "[TEXT]");
        if (textSection != null && normalizeEvidenceText(textSection).length() <= 300) {
            String fromTextSection = firstMeaningfulLine(textSection);
            if (fromTextSection != null) return clipEvidence(fromTextSection);
        }

        String normalized = normalizeEvidenceText(userText);
        if (!normalized.isBlank() && normalized.length() <= 200) {
            return clipEvidence(normalized);
        }
        return null;
    }

    private static String afterMarker(String text, String marker) {
        if (text == null || marker == null || marker.isBlank()) return null;
        int idx = text.indexOf(marker);
        if (idx < 0) return null;
        return text.substring(idx + marker.length()).trim();
    }

    private static String firstMeaningfulLine(String text) {
        if (text == null || text.isBlank()) return null;
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String normalized = normalizeEvidenceText(line);
            if (normalized.isBlank()) continue;
            if (normalized.startsWith("[")) continue;
            return normalized;
        }
        return null;
    }

    private static String normalizeEvidenceText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String clipEvidence(String text) {
        if (text == null) return null;
        String normalized = normalizeEvidenceText(text);
        if (normalized.isBlank()) return null;
        if (normalized.length() < 2) return null;
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    boolean hasVerifiableEvidence(String inputText, List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) return false;
        String t = inputText == null ? "" : inputText;
        if (t.isBlank()) return false;
        for (String ev : evidence) {
            if (ev == null) continue;
            String s = ev.trim();
            if (s.isBlank()) continue;
            List<String> ph = extractImagePlaceholders(s);
            if (!ph.isEmpty()) {
                for (String p : ph) {
                    if (t.contains(p)) return true;
                }
                continue;
            }
            if (s.startsWith("{") || s.startsWith("[")) {
                if (hasVerifiableJsonEvidence(t, s)) return true;
                continue;
            }
            if (s.length() > 200) s = s.substring(0, 200);
            if (t.contains(s)) return true;
        }
        return false;
    }

    private static List<String> extractImagePlaceholders(String text) {
        if (text == null) return List.of();
        Matcher matcher = IMAGE_PLACEHOLDER.matcher(text);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        while (matcher.find()) {
            String idx = matcher.group(1);
            if (idx == null) continue;
            String trimmed = idx.trim();
            if (trimmed.isEmpty()) continue;
            out.add("[[IMAGE_" + trimmed + "]]");
        }
        return new ArrayList<>(out);
    }

    private boolean hasVerifiableJsonEvidence(String inputText, String json) {
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n.isObject()) {
                return hasVerifiableEvidenceObject(inputText, n);
            }
            if (n.isArray()) {
                for (JsonNode it : n) {
                    if (it == null) continue;
                    if (!it.isObject()) continue;
                    if (hasVerifiableEvidenceObject(inputText, it)) return true;
                }
                return false;
            }
            return false;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static boolean hasVerifiableEvidenceObject(String inputText, JsonNode obj) {
        if (obj == null || !obj.isObject()) return false;
        String t = inputText == null ? "" : inputText;
        if (t.isBlank()) return false;

        // Format 1: before_context / after_context anchors
        String beforeCtx = textOrNull(obj.path("before_context"));
        if (beforeCtx != null && !beforeCtx.isBlank()) {
            String bc = beforeCtx.trim();
            if (t.contains(bc)) return true;
            return normalizeForAnchorMatch(t).contains(normalizeForAnchorMatch(bc));
        }

        // Format 2: quote
        String quote = textOrNull(obj.path("quote"));
        if (quote != null) {
            String q = quote.trim();
            if (q.length() > 200) q = q.substring(0, 200);
            return !q.isBlank() && t.contains(q);
        }

        return false;
    }

    private String extractAssistantContent(String rawJson) {
        return AiResponseParsingUtils.extractAssistantContent(objectMapper, rawJson);
    }

    ParsedDecision parseDecisionFromAssistantText(String assistantText) {
        if (assistantText == null) assistantText = "";
        String t = assistantText.trim();

        int l = t.indexOf('{');
        int r = t.lastIndexOf('}');
        String json = (l >= 0 && r > l) ? t.substring(l, r + 1) : t;

        try {
            return parseDecisionFromJsonOrThrow(json);
        } catch (Exception e) {
            String recovered = recoverTruncatedJson(json);
            if (recovered != null && !recovered.isBlank() && !recovered.equals(json)) {
                try {
                    ParsedDecision out = parseDecisionFromJsonOrThrow(recovered);
                    if (out.reasons == null) out.reasons = new ArrayList<>();
                    out.reasons.add("模型输出 JSON 被截断，已进行恢复解析");
                    return out;
                } catch (Exception ignore) {
                }
            }

            ParsedDecision partial = extractPartialDecision(t);
            if (partial != null) {
                if (partial.reasons == null) partial.reasons = new ArrayList<>();
                partial.reasons.add("模型输出 JSON 被截断，已提取部分字段");
                return partial;
            }

            ParsedDecision out = new ParsedDecision();
            out.decisionSuggestion = "ESCALATE";
            out.decision = "HUMAN";
            out.score = null;
            out.riskScore = null;
            out.reasons = List.of("Model output could not be parsed as JSON: " + e.getMessage());
            out.labels = List.of();
            out.riskTags = List.of("PARSE_ERROR");
            return out;
        }
    }

    private ParsedDecision parseDecisionFromJsonOrThrow(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        ParsedDecision out = new ParsedDecision();
        out.decisionSuggestion = normalizeSuggestion(firstTextOrNull(root, List.of("decision_suggestion", "decision")));

        if (root.path("image_risk_score").isNumber()) out.score = root.path("image_risk_score").asDouble();
        if (out.score == null && root.path("risk_score").isNumber()) out.score = root.path("risk_score").asDouble();
        if (out.score == null && root.path("score").isNumber()) out.score = root.path("score").asDouble();
        out.riskScore = out.score;

        out.reasons = new ArrayList<>();
        JsonNode reasons = root.path("reasons");
        if (reasons.isArray()) {
            for (JsonNode n : reasons) {
                if (n.isTextual()) out.reasons.add(n.asText());
            }
        }
        String reason = textOrNull(root.path("reason"));
        if (reason != null && !reason.isBlank() && !out.reasons.contains(reason)) {
            out.reasons.add(reason);
        }

        out.labels = new ArrayList<>();
        appendUniqueTextualLabels(out.labels, root.path("image_labels"));
        List<String> riskTagsList = new ArrayList<>();
        appendUniqueTextualLabels(riskTagsList, root.path("riskTags"));
        appendUniqueTextualLabels(riskTagsList, root.path("risk_tags"));
        JsonNode labels = root.path("labels");
        appendUniqueTextualLabels(out.labels, labels);
        if (!riskTagsList.isEmpty()) out.riskTags = riskTagsList;
        else out.riskTags = out.labels == null ? List.of() : out.labels;

        out.severity = firstTextOrNull(root, List.of("severity"));
        if (root.path("uncertainty").isNumber()) out.uncertainty = root.path("uncertainty").asDouble();

        out.evidence = new ArrayList<>();
        JsonNode evidence = root.path("evidence");
        if (evidence.isArray()) {
            for (JsonNode n : evidence) {
                if (n.isTextual()) {
                    String ev = n.asText();
                    if (ev != null && !ev.isBlank()) out.evidence.add(ev);
                    continue;
                }
                if (n.isObject() || n.isArray()) {
                    try {
                        String ev = objectMapper.writeValueAsString(n);
                        if (ev != null && !ev.isBlank()) out.evidence.add(ev);
                    } catch (Exception ignore) {
                    }
                }
            }
        } else if (evidence.isObject()) {
            try {
                String ev = objectMapper.writeValueAsString(evidence);
                if (ev != null && !ev.isBlank()) out.evidence.add(ev);
            } catch (Exception ignore) {
            }
        } else if (evidence.isTextual()) {
            String ev = evidence.asText();
            if (ev != null && !ev.isBlank()) out.evidence.add(ev);
        }

        out.description = textOrNull(root.path("description"));
        if (out.description == null || out.description.isBlank()) {
            out.description = textOrNull(root.path("imageDescription"));
        }
        out.description = trimToMaxChars(out.description);

        if (out.decisionSuggestion == null || out.decisionSuggestion.isBlank()) {
            Boolean safe = booleanOrNull(root.path("safe"));
            if (safe != null) {
                out.decisionSuggestion = safe ? "ALLOW" : "REJECT";
            }
        }
        if (out.decisionSuggestion == null || out.decisionSuggestion.isBlank()) out.decisionSuggestion = "ESCALATE";
        out.decision = suggestionToDecision(out.decisionSuggestion, out.score);
        if (out.score != null) {
            if (out.score < 0) out.score = 0.0;
            if (out.score > 1) out.score = 1.0;
        }
        if (out.uncertainty != null) {
            if (!Double.isFinite(out.uncertainty)) out.uncertainty = null;
            else {
                if (out.uncertainty < 0) out.uncertainty = 0.0;
                if (out.uncertainty > 1) out.uncertainty = 1.0;
            }
        }
        return out;
    }

    private static void appendUniqueTextualLabels(List<String> out, JsonNode node) {
        if (out == null || node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) {
            for (JsonNode item : node) {
                appendUniqueTextualLabels(out, item);
            }
            return;
        }
        if (!node.isTextual()) return;
        String tag = node.asText();
        if (tag == null || tag.isBlank() || out.contains(tag)) return;
        out.add(tag);
    }

    private static String recoverTruncatedJson(String text) {
        if (text == null) return null;
        String t = text.trim();
        int l = t.indexOf('{');
        if (l < 0) return null;
        int cutKey = -1;
        for (String k : List.of("\"description\"", "\"imageDescription\"")) {
            int idx = t.indexOf(k);
            if (idx >= 0 && (cutKey < 0 || idx < cutKey)) cutKey = idx;
        }
        if (cutKey < 0) return null;
        int cut = t.lastIndexOf(',', cutKey);
        if (cut < 0) cut = cutKey - 1;
        if (cut <= l) return null;
        String head = t.substring(l, cut).trim();
        while (head.endsWith(",")) head = head.substring(0, head.length() - 1).trim();
        int open = countChar(head, '{');
        int close = countChar(head, '}');
        StringBuilder sb = new StringBuilder(head);
        sb.repeat("}", Math.clamp(open - close, 0, 8));
        String out = sb.toString().trim();
        return out.endsWith("}") ? out : (out + "}");
    }

    private static int countChar(String s, char c) {
        if (s == null || s.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) n++;
        }
        return n;
    }

    private ParsedDecision extractPartialDecision(String text) {
        if (text == null || text.isBlank()) return null;
        String decisionRaw = firstGroup(text, Pattern.compile("\"decision\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE));
        String suggestionRaw = firstGroup(text, Pattern.compile("\"decision_suggestion\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE));
        String sug = normalizeSuggestion(suggestionRaw != null ? suggestionRaw : decisionRaw);
        if (sug == null || sug.isBlank()) return null;

        Double score = null;
        String scoreRaw = firstGroup(text, Pattern.compile("\"(image_risk_score|risk_score|score)\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE));
        if (scoreRaw != null) {
            try {
                score = Double.parseDouble(scoreRaw);
            } catch (Exception ignore) {
            }
        }

        ParsedDecision out = new ParsedDecision();
        out.decisionSuggestion = sug;
        out.score = score;
        out.riskScore = score;
        out.decision = suggestionToDecision(out.decisionSuggestion, out.score);
        out.labels = List.of();
        out.riskTags = List.of();
        out.description = null;
        return out;
    }

    private static String firstGroup(String text, Pattern p) {
        if (text == null || p == null) return null;
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        if (m.groupCount() >= 2) return m.group(2);
        if (m.groupCount() == 1) return m.group(1);
        return null;
    }

    private static String trimToMaxChars(String s) {
        if (s == null) return null;
        int limit = Math.max(0, 400);
        String t = s.trim();
        if (t.length() <= limit) return t;
        return t.substring(0, limit);
    }

    private static String partsToDebugText(List<?> parts) {
        if (parts == null || parts.isEmpty()) return "[non_text_content]";
        StringBuilder sb = new StringBuilder();
        for (Object it : parts) {
            if (!(it instanceof Map<?, ?> m)) continue;
            Object tv = m.get("type");
            String type = tv == null ? null : tv.toString();
            if (type == null) continue;
            if (type.equals("text")) {
                Object text = m.get("text");
                if (text != null) sb.append(text);
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            } else if (type.equals("image_url")) {
                Object iu = m.get("image_url");
                if (iu instanceof Map<?, ?> im) {
                    Object u = im.get("url");
                    if (u != null) {
                        sb.append("[image] ").append(u);
                        if (sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                    }
                }
            }
            if (sb.length() >= 2000) break;
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "[non_text_content]" : out;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        return String.valueOf(n);
    }

    private static String firstTextOrNull(JsonNode root, List<String> keys) {
        if (root == null || keys == null || keys.isEmpty()) return null;
        for (String key : keys) {
            if (key == null || key.isBlank()) continue;
            String v = textOrNull(root.path(key));
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static Boolean booleanOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isBoolean()) return n.asBoolean();
        if (n.isTextual()) {
            String t = n.asText();
            if (t == null) return null;
            String s = t.trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("y") || s.equals("1")) return Boolean.TRUE;
            if (s.equals("false") || s.equals("no") || s.equals("n") || s.equals("0")) return Boolean.FALSE;
        }
        return null;
    }

    private static List<String> mergeTags(List<String> a, List<String> b) {
        return ModerationTagSupport.mergeTags(a, b);
    }

    private static String maxSeverity(String a, String b) {
        int ra = severityRank(a);
        int rb = severityRank(b);
        return rb >= ra ? b : a;
    }

    private static int severityRank(String s) {
        if (s == null) return 0;
        String t = s.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "LOW" -> 1;
            case "MID", "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    private static String normalizeDecision(String decision, Double score) {
        if (decision == null) return null;
        String d = decision.trim().toUpperCase(Locale.ROOT);
        switch (d) {
            case "APPROVE", "REJECT", "HUMAN" -> {
                return d;
            }
            case "ALLOW" -> {
                return "APPROVE";
            }
            case "ESCALATE" -> {
                return "HUMAN";
            }
        }
        if (decision.contains("通过")) return "APPROVE";
        if (decision.contains("拒绝") || decision.contains("违规")) return "REJECT";
        if (decision.contains("人工")) return "HUMAN";
        if (score != null) return score >= 0.75 ? "REJECT" : "APPROVE";
        return "HUMAN";
    }

    private static String normalizeSuggestion(String decisionSuggestion) {
        if (decisionSuggestion == null) return null;
        String d = decisionSuggestion.trim().toUpperCase(Locale.ROOT);
        switch (d) {
            case "ALLOW", "REJECT", "ESCALATE" -> {
                return d;
            }
            case "APPROVE" -> {
                return "ALLOW";
            }
            case "HUMAN" -> {
                return "ESCALATE";
            }
        }
        if (decisionSuggestion.contains("通过")) return "ALLOW";
        if (decisionSuggestion.contains("拒绝") || decisionSuggestion.contains("违规")) return "REJECT";
        if (decisionSuggestion.contains("人工")) return "ESCALATE";
        return d;
    }

    private static String suggestionToDecision(String suggestion, Double score) {
        String s = normalizeSuggestion(suggestion);
        if (s == null || s.isBlank()) return normalizeDecision(suggestion, score);
        if (s.equals("ALLOW")) return "APPROVE";
        if (s.equals("REJECT")) return "REJECT";
        return "HUMAN";
    }

    static String decisionToSuggestion(String decision) {
        if (decision == null) return null;
        String d = decision.trim().toUpperCase(Locale.ROOT);
        return switch (d) {
            case "REJECT" -> "REJECT";
            case "APPROVE" -> "ALLOW";
            case "HUMAN" -> "ESCALATE";
            default -> null;
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @SuppressWarnings("unchecked")
    List<String> enrichEvidenceWithText(List<String> evidence, String auditText) {
        if (evidence == null || evidence.isEmpty() || auditText == null || auditText.isBlank()) return evidence;
        ArrayList<String> out = new ArrayList<>();
        for (String rawEvidence : evidence) {
            if (rawEvidence == null) { out.add(null); continue; }
            String s = rawEvidence.trim();
            if (!s.startsWith("{") || !s.endsWith("}")) {
                out.add(rawEvidence);
                continue;
            }
            try {
                Map<String, Object> node = objectMapper.readValue(s, Map.class);
                if (node == null) { out.add(rawEvidence); continue; }
                Object textObj = node.get("text");
                if (textObj instanceof String existing && !existing.trim().isBlank()
                        && !isSuspiciousEvidenceText(existing.trim(), auditText)) {
                    out.add(rawEvidence);
                    continue;
                }

                String snippet = extractByContextAnchors(node, auditText);
                if (snippet == null || snippet.isBlank()) { out.add(rawEvidence); continue; }
                snippet = IMAGE_PLACEHOLDER.matcher(snippet).replaceAll("").trim();
                if (snippet.isBlank()) { out.add(rawEvidence); continue; }
                String cleaned = cleanExtractedSnippet(snippet);
                if (cleaned.isBlank()) { out.add(rawEvidence); continue; }
                String clipped = cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
                node.put("text", clipped);
                out.add(objectMapper.writeValueAsString(node));
            } catch (Exception e2) {
                out.add(rawEvidence);
            }
        }
        return out;
    }

    private static String extractByContextAnchors(Map<String, Object> node, String auditText) {
        ModerationAnchorSnippetSupport.AnchorContext context = ModerationAnchorSnippetSupport.readAnchorContext(node);
        if (context == null) return null;

        if (auditText == null || auditText.isBlank()) return null;
        String r = extractBetweenAnchorsByRegex(auditText, context.before(), context.after());
        return r == null || r.isBlank() ? null : r;
    }

    private static String normalizeForAnchorMatch(String s) {
        if (s == null) return "";
        return s.replace('“', '"').replace('”', '"')
                .replace('‘', '\'').replace('’', '\'')
                .replaceAll("\\s+", " ")
                .replaceAll(" ?\" ?", "\"")
                .replaceAll(" ?' ?", "'");
    }

    @SuppressWarnings("unused")
    private static String normalizeForAnchorRegex(String s) {
        return normalizeForAnchorMatch(s);
    }

    @SuppressWarnings("unused")
    private static String anchorToRegex(String anchor) {
        return ModerationAnchorSnippetSupport.anchorToRegex(anchor);
    }

    @SuppressWarnings("unused")
    private static int findBoundaryEnd(String text, int start, int maxEnd) {
        if (text == null || text.isEmpty()) return 0;
        int s = Math.max(0, start);
        int end = Math.clamp(s, maxEnd, text.length());
        for (int i = s; i < end; i++) {
            char ch = text.charAt(i);
            if (ch == '\n' || ch == '\r') return i;
            if (ch == ',' || ch == '，' || ch == '.' || ch == '!' || ch == '?' || ch == ';' || ch == ':'
                    || ch == '。' || ch == '！' || ch == '？' || ch == '；' || ch == '：') {
                return i;
            }
        }
        return end;
    }

    private static String fallbackViolationSnippet(String text, int violationStart) {
        return ModerationViolationSnippetSupport.fallbackViolationSnippet(
                text,
                violationStart,
                "\n[",
                "\n\n",
                c -> c == '\n' || c == '\r' || c == '。' || c == '！' || c == '？' || c == ';' || c == '!' || c == '?',
                AdminModerationLlmUpstreamSupport::cleanExtractedSnippet
        );
    }

    private static String cleanExtractedSnippet(String snippet) {
        if (snippet == null) return "";
        String cleaned = IMAGE_PLACEHOLDER.matcher(snippet).replaceAll(" ").trim();
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ").trim();
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static boolean isSuspiciousEvidenceText(String text, String textSection) {
        String t = text == null ? "" : text.trim();
        if (t.isBlank()) return true;
        if (t.length() > 320) return true;
        if (textSection == null || textSection.isBlank()) return false;
        if (textSection.contains(t)) return false;
        String normSection = normalizeForAnchorMatch(textSection);
        String normText = normalizeForAnchorMatch(t);
        return normText.length() >= 6 && !normSection.contains(normText);
    }

    private static String extractBetweenAnchorsByRegex(String text, String before, String after) {
        return ModerationAnchorSnippetSupport.extractBetweenAnchorsByRegex(
                text,
                before,
                after,
                Math.clamp(500, 20, 2000),
                AdminModerationLlmUpstreamSupport::cleanExtractedSnippet,
                AdminModerationLlmUpstreamSupport::fallbackViolationSnippet
        );
    }

}
