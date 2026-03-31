package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlmGateway {

    private static final String LEGACY_UPSTREAM_CALL_FAILED = "涓婃父AI璋冪敤澶辫触";
    private static final String LEGACY_UPSTREAM_STREAM_FAILED = "涓婃父AI娴佸紡璋冪敤澶辫触";
    private static final String LEGACY_ROUTE_TARGET = "璺敱鐩爣";
    private static final String LEGACY_UNSUPPORTED_PROVIDER = "鏆備笉鏀寔鐨勬ā鍨嬫彁渚涘晢绫诲瀷";
    private static final String LEGACY_OUTPUT_TEXT = "鏉堟挸鍤弬鍥ㄦ拱:";

    private final AiProvidersConfigService aiProvidersConfigService;
    private final AiEmbeddingService aiEmbeddingService;
    private final AiRerankService aiRerankService;
    private final LlmCallQueueService llmCallQueueService;
    private final LlmRoutingService llmRoutingService;
    private final LlmRoutingTelemetryService llmRoutingTelemetryService;
    private final TokenCountService tokenCountService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger rrCounter = new AtomicInteger(0);

    private static String compatLabel(String primary, String legacy) {
        if (legacy == null || legacy.isBlank()) return primary;
        if (primary == null || primary.isBlank()) return legacy;
        return primary + "(" + legacy + ")";
    }

    private static String upstreamCallFailedPrefix() {
        return compatLabel("上游AI调用失败", LEGACY_UPSTREAM_CALL_FAILED);
    }

    private static String upstreamStreamFailedPrefix() {
        return compatLabel("上游AI流式调用失败", LEGACY_UPSTREAM_STREAM_FAILED);
    }

    private static String routeTargetLabel() {
        return compatLabel("路由目标", LEGACY_ROUTE_TARGET);
    }

    private static String unsupportedProviderTypePrefix() {
        return compatLabel("暂不支持的模型提供商类型", LEGACY_UNSUPPORTED_PROVIDER);
    }

    private static String outputTextLabel() {
        return compatLabel("输出文本:", LEGACY_OUTPUT_TEXT);
    }

    public record RoutedChatOnceResult(
            String text,
            String providerId,
            String model,
            LlmCallQueueService.UsageMetrics usage
    ) {}

    public record RoutedChatStreamResult(
            String providerId,
            String model,
            LlmCallQueueService.UsageMetrics usage
    ) {}

    public String chatOnce(
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature
    ) {
        return chatOnce(LlmQueueTaskType.MULTIMODAL_CHAT, providerId, modelOverride, messages, temperature);
    }

    public String chatOnce(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature).text();
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, null, null, null);
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Integer maxTokens,
            List<String> stop
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, null, maxTokens, stop);
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, stop, null, null);
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, stop, enableThinking, null);
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, null);
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody
    ) {
        return chatOnceRouted(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, null);
    }

    private static boolean shouldStripThinkBlocks(Boolean enableThinking) {
        return LlmGatewaySupport.shouldStripThinkBlocks(enableThinking);
    }

    public RoutedChatOnceResult chatOnceRoutedNoQueue(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody
    ) {
        return chatOnceRoutedNoQueue(taskType, providerId, modelOverride, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, null);
    }

    private static boolean shouldPreferTokenizerIn(LlmQueueTaskType taskType) {
        return LlmGatewaySupport.shouldPreferTokenizerIn(taskType);
    }

    public void chatStream(
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            OpenAiCompatClient.SseLineConsumer consumer
    ) {
        chatStream(LlmQueueTaskType.MULTIMODAL_CHAT, providerId, modelOverride, messages, temperature, null, null, consumer);
    }

    public void chatStream(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Boolean enableThinking,
            Integer thinkingBudget,
            OpenAiCompatClient.SseLineConsumer consumer
    ) {
        chatStreamRouted(taskType, providerId, modelOverride, messages, temperature, enableThinking, thinkingBudget, consumer);
    }

    public RoutedChatStreamResult chatStreamRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Boolean enableThinking,
            Integer thinkingBudget,
            OpenAiCompatClient.SseLineConsumer consumer
    ) {
        return chatStreamRouted(taskType, providerId, modelOverride, messages, temperature, null, enableThinking, thinkingBudget, consumer);
    }

    private static boolean supportsThinkingDirectiveModel(String modelName) {
        return LlmGatewaySupport.supportsThinkingDirectiveModel(modelName);
    }

    public AiEmbeddingService.EmbeddingResult embedOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            String input
    ) throws IOException {
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.EMBEDDING : taskType;
        String pid = providerId == null ? null : providerId.trim();
        String mo = modelOverride == null ? null : modelOverride.trim();

        if (mo != null && !mo.isBlank()) {
            return aiEmbeddingService.embedOnceForTask(input, mo, pid, tt);
        }
        boolean providerOnly = (pid != null && !pid.isBlank());

        LlmRoutingService.Policy policy = llmRoutingService.getPolicy(tt);
        Set<LlmRoutingService.TargetId> tried = new java.util.HashSet<>();
        Exception last = null;

        for (int attempt = 1; attempt <= Math.max(1, policy.maxAttempts()); attempt++) {
            LlmRoutingService.RouteTarget target = providerOnly
                    ? llmRoutingService.pickNextInProvider(tt, pid, tried)
                    : llmRoutingService.pickNext(tt, tried);
            if (target == null) {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_NO_TARGET",
                        tt.name(),
                        attempt,
                        null,
                        providerOnly ? pid : null,
                        null,
                        false,
                        "",
                        providerOnly ? ("no eligible target (providerId=" + pid + ")") : "no eligible target",
                        0L,
                        "embeddingOnce"
                ));
                break;
            }
            tried.add(target.id());

            String model = target.modelName();
            AtomicReference<String> taskIdRef = new AtomicReference<>(null);
            long startedNs = System.nanoTime();
            try {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_ATTEMPT",
                        tt.name(),
                        attempt,
                        null,
                        target.providerId(),
                        model,
                        null,
                        "",
                        "",
                        null,
                        "embeddingOnce"
                ));
                AiEmbeddingService.EmbeddingResult res = aiEmbeddingService.embedOnceForTask(input, model, target.providerId(), tt);
                llmRoutingService.recordSuccess(tt, target);
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_OK",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        target.providerId(),
                        model,
                        true,
                        "",
                        "",
                        elapsedMs(startedNs),
                        "embeddingOnce"
                ));
                return res;
            } catch (Exception e) {
                last = e;
                if (!isRetriable(e)) {
                    if (e instanceof IOException ioe) throw ioe;
                    throw new IOException("Embedding failed: " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(e));
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_FAIL",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        target.providerId(),
                        model,
                        false,
                        safeErrorCode(e),
                        safeErrorMessage(e),
                        elapsedMs(startedNs),
                        "embeddingOnce"
                ));
            }
        }

        if (last instanceof IOException ioe) throw ioe;
        if (last != null) throw new IOException("Embedding failed: " + last.getMessage(), last);
        throw new IOException(providerOnly
                ? ("Embedding failed: no eligible upstream target for providerId=" + pid + " (please check embedding routing config)")
                : "Embedding failed: no eligible upstream target (please check embedding routing config)");
    }

    public AiRerankService.RerankResult rerankOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            String query,
            List<String> documents,
            Integer topN,
            String instruct,
            Boolean returnDocuments,
            Double fps
    ) throws IOException {
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.RERANK : taskType;
        String pid = providerId == null ? null : providerId.trim();
        String mo = modelOverride == null ? null : modelOverride.trim();

        if (mo != null && !mo.isBlank()) {
            return aiRerankService.rerankOnce(pid, mo, query, documents, topN, instruct, returnDocuments, fps);
        }
        if (pid != null && !pid.isBlank()) {
            return aiRerankService.rerankOnce(pid, null, query, documents, topN, instruct, returnDocuments, fps);
        }

        LlmRoutingService.Policy policy = llmRoutingService.getPolicy(tt);
        Set<LlmRoutingService.TargetId> tried = new java.util.HashSet<>();
        Exception last = null;

        for (int attempt = 1; attempt <= Math.max(1, policy.maxAttempts()); attempt++) {
            LlmRoutingService.RouteTarget target = llmRoutingService.pickNext(tt, tried);
            if (target == null) {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_NO_TARGET",
                        tt.name(),
                        attempt,
                        null,
                        null,
                        null,
                        false,
                        "",
                        "no eligible target",
                        0L,
                        "rerankOnce"
                ));
                break;
            }
            tried.add(target.id());

            String model = target.modelName();
            AtomicReference<String> taskIdRef = new AtomicReference<>(null);
            long startedNs = System.nanoTime();
            try {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_ATTEMPT",
                        tt.name(),
                        attempt,
                        null,
                        target.providerId(),
                        model,
                        null,
                        "",
                        "",
                        null,
                        "rerankOnce"
                ));
                AiRerankService.RerankResult res = aiRerankService.rerankOnce(target.providerId(), model, query, documents, topN, instruct, returnDocuments, fps);
                llmRoutingService.recordSuccess(tt, target);
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_OK",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        target.providerId(),
                        model,
                        true,
                        "",
                        "",
                        elapsedMs(startedNs),
                        "rerankOnce"
                ));
                return res;
            } catch (Exception e) {
                last = e;
                if (!isRetriable(e)) {
                    if (e instanceof IOException ioe) throw ioe;
                    throw new IOException("Rerank failed: " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(e));
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_FAIL",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        target.providerId(),
                        model,
                        false,
                        safeErrorCode(e),
                        safeErrorMessage(e),
                        elapsedMs(startedNs),
                        "rerankOnce"
                ));
            }
        }

        if (last instanceof IOException ioe) throw ioe;
        if (last != null) throw new IOException("Rerank failed: " + last.getMessage(), last);
        throw new IOException("Rerank failed: no eligible upstream target");
    }

    private static String applyThinkingDirective(String content, boolean enableThinking, String modelName) {
        return LlmGatewaySupport.applyThinkingDirective(content, enableThinking, modelName);
    }

    private String pickFallbackProviderId(java.util.function.Function<AiProvidersConfigService.ResolvedProvider, String> modelExtractor) {
        List<String> ids = aiProvidersConfigService.listEnabledProviderIds();
        if (ids == null || ids.isEmpty()) return null;

        java.util.List<String> validIds = new java.util.ArrayList<>();
        for (String id : ids) {
            try {
                AiProvidersConfigService.ResolvedProvider p = resolve(id);
                if (p != null) {
                    String model = modelExtractor.apply(p);
                    if (model != null && !model.isBlank()) {
                        validIds.add(id);
                    }
                }
            } catch (Exception ignore) {
            }
        }

        if (validIds.isEmpty()) return null;
        int idx = Math.floorMod(rrCounter.getAndIncrement(), validIds.size());
        return validIds.get(idx);
    }

    private record ChatOnceInternalResult(String text, LlmCallQueueService.UsageMetrics usage) {}

    private static Map<String, String> mergeHeaders(Map<String, String> base, Map<String, String> extra) {
        return LlmGatewaySupport.mergeHeaders(base, extra);
    }

    private static long elapsedMs(long startedNs) {
        return LlmGatewaySupport.elapsedMs(startedNs);
    }

    private static String safeErrorCode(Throwable e) {
        return LlmGatewaySupport.safeErrorCode(e);
    }

    private static String safeErrorMessage(Throwable e) {
        return LlmGatewaySupport.safeErrorMessage(e);
    }

    private static List<ChatMessage> applyThinkingDirectiveToMessages(List<ChatMessage> messages, Boolean enableThinking, String modelName) {
        return LlmGatewaySupport.applyThinkingDirectiveToMessages(messages, enableThinking, modelName);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
        return LlmGatewaySupport.indexOfIgnoreCase(haystack, needle, fromIndex);
    }

    private static int minPositive(int a, int b) {
        return LlmGatewaySupport.minPositive(a, b);
    }

    private static String stripThinkBlocks(String text) {
        return LlmGatewaySupport.stripThinkBlocks(text);
    }

    private static String removeMarkerWordIgnoreCase(String text, String marker) {
        return LlmGatewaySupport.removeMarkerWordIgnoreCase(text, marker);
    }

    private static String removeClosedReasoningBlocks(String text) {
        return LlmGatewaySupport.removeClosedReasoningBlocks(text);
    }

    private static String stripReasoningArtifacts(String text) {
        return LlmGatewaySupport.stripReasoningArtifacts(text);
    }

    private static String removeClosedThinkBlocks(String text) {
        return LlmGatewaySupport.removeClosedThinkBlocks(text);
    }

    private static final class ThinkStripper {
        private boolean inThink = false;
        private String carry = "";

        private long appendLimited(StringBuilder out, String seg, int maxChars) {
            if (seg == null || seg.isEmpty()) return 0L;
            long produced = seg.length();
            if (out != null) {
                int remain = maxChars - out.length();
                if (remain > 0) {
                    int take = Math.min(remain, seg.length());
                    out.append(seg, 0, take);
                }
            }
            return produced;
        }

        private boolean isEscapedStartAt(String s, int pos) {
            return pos >= 0 && pos + 8 <= s.length() && indexOfIgnoreCase(s, "&lt;think", pos) == pos;
        }

        private boolean isRawStartAt(String s, int pos) {
            return pos >= 0 && pos + 6 <= s.length() && indexOfIgnoreCase(s, "<think", pos) == pos;
        }

        private int findNextStart(String s, int from) {
            return minPositive(
                    indexOfIgnoreCase(s, "<think", from),
                    indexOfIgnoreCase(s, "&lt;think", from)
            );
        }

        private int findNextClose(String s, int from) {
            return minPositive(
                    indexOfIgnoreCase(s, "</think>", from),
                    indexOfIgnoreCase(s, "&lt;/think&gt;", from)
            );
        }

        long accept(String chunk, StringBuilder out, int maxChars) {
            if (chunk == null || chunk.isEmpty()) return 0L;
            String input = carry.isEmpty() ? chunk : (carry + chunk);
            carry = "";
            long produced = 0L;
            int i = 0;
            while (i < input.length()) {
                if (!inThink) {
                    int start = findNextStart(input, i);
                    if (start < 0) {
                        produced += appendLimited(out, input.substring(i), maxChars);
                        return produced;
                    }
                    if (start > i) {
                        produced += appendLimited(out, input.substring(i, start), maxChars);
                    }
                    boolean escaped = isEscapedStartAt(input, start);
                    int openEndExclusive;
                    if (escaped) {
                        int gt = indexOfIgnoreCase(input, "&gt;", start);
                        if (gt < 0) {
                            carry = input.substring(start);
                            return produced;
                        }
                        openEndExclusive = gt + "&gt;".length();
                    } else {
                        int gt = input.indexOf('>', start);
                        if (gt < 0) {
                            carry = input.substring(start);
                            return produced;
                        }
                        openEndExclusive = gt + 1;
                    }
                    inThink = true;
                    i = openEndExclusive;
                } else {
                    int close = findNextClose(input, i);
                    if (close < 0) {
                        int tail = Math.max(i, input.length() - 32);
                        carry = input.substring(tail);
                        return produced;
                    }
                    boolean escaped = indexOfIgnoreCase(input, "&lt;/think&gt;", close) == close;
                    int closeEndExclusive = close + (escaped ? "&lt;/think&gt;".length() : "</think>".length());
                    inThink = false;
                    i = closeEndExclusive;
                }
            }
            return produced;
        }
    }

    private static boolean shouldSendDashscopeThinking(AiProvidersConfigService.ResolvedProvider provider) {
        return LlmGatewaySupport.shouldSendDashscopeThinking(provider);
    }

    private static void withStreamRetry(
            int maxAttempts,
            OpenAiCompatClient.ChatRequest req,
            OpenAiCompatClient client,
            OpenAiCompatClient.SseLineConsumer consumer
    ) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            AtomicBoolean started = new AtomicBoolean(false);
            OpenAiCompatClient.SseLineConsumer wrapped = (line) -> {
                if (!started.get() && line != null) {
                    String t = line.trim();
                    if (t.startsWith("data:") && !t.equals("data: [DONE]")) {
                        started.set(true);
                    }
                }
                consumer.onLine(line);
            };

            try {
                client.chatCompletionsStream(req, wrapped);
                return;
            } catch (Exception e) {
                last = e;
                if (started.get()) {
                    throw e;
                }
                if (i >= attempts || !isRetriable(e)) {
                    throw e;
                }
                sleepBackoff(i);
            }
        }
        throw new IllegalStateException("Unexpected retry exit", last);
    }

    private static Map<String, Object> filterExtraBody(AiProvidersConfigService.ResolvedProvider provider, Map<String, Object> extraBody) {
        return LlmGatewaySupport.filterExtraBody(provider, extraBody);
    }

    private static boolean isRetriable(Throwable e) {
        return LlmGatewaySupport.isRetriable(e);
    }

    private static String extractErrorCode(Throwable e) {
        return LlmGatewaySupport.extractErrorCode(e);
    }

    private static Integer asIntLoose(JsonNode n) {
        return LlmGatewaySupport.asIntLoose(n);
    }

    private static Integer pickIntLoose(JsonNode obj, String... keys) {
        return LlmGatewaySupport.pickIntLoose(obj, keys);
    }

    private static LlmCallQueueService.UsageMetrics normalizeOpenAiCompatUsage(Integer prompt, Integer completion, Integer total) {
        return LlmGatewaySupport.normalizeOpenAiCompatUsage(prompt, completion, total);
    }

    private static <T> T withRetry(int maxAttempts, CheckedSupplier<T> call) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return call.get();
            } catch (Exception e) {
                last = e;
                if (i >= attempts || !isRetriable(e)) {
                    throw e;
                }
                sleepBackoff(i);
            }
        }
        throw last == null ? new IllegalStateException("调用失败") : last;
    }

    private ChatOnceInternalResult callChatOnceSingleNoQueue(
            LlmQueueTaskType taskType,
            AiProvidersConfigService.ResolvedProvider provider,
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody,
            int httpAttempts,
            Map<String, String> extraRequestHeaders
    ) throws Exception {
        OpenAiCompatClient client = new OpenAiCompatClient();
        List<ChatMessage> patchedMessages = applyThinkingDirectiveToMessages(messages, enableThinking, model);
        boolean stripThink = shouldStripThinkBlocks(enableThinking);
        boolean isDashscope = shouldSendDashscopeThinking(provider);
        Boolean enableThinkingToSend = isDashscope ? Boolean.FALSE : null;
        Integer thinkingBudgetToSend = null;
        Map<String, Object> extraBodyToSend = filterExtraBody(provider, extraBody);
        OpenAiCompatClient.ChatRequest req = new OpenAiCompatClient.ChatRequest(
                provider.apiKey(),
                provider.baseUrl(),
                model,
            patchedMessages,
                temperature,
                topP,
                maxTokens,
                stop,
                enableThinkingToSend,
                thinkingBudgetToSend,
                extraBodyToSend,
                mergeHeaders(provider.extraHeaders(), extraRequestHeaders),
                provider.connectTimeoutMs(),
                provider.readTimeoutMs(),
                false
        );
        String raw = withRetry(Math.max(1, httpAttempts), () -> client.chatCompletionsOnce(req));
        String assistantRaw = extractAssistantContent(raw);
        String assistant = stripThink
                ? stripReasoningArtifacts(stripThinkBlocks(assistantRaw))
                : assistantRaw;
        LlmCallQueueService.UsageMetrics usage = llmCallQueueService.parseOpenAiUsageFromJson(raw);
        if (isUsageIncomplete(usage)) {
            int estIn = estimateInputTokens(patchedMessages);
            String assistantForTokens = assistant;
            int estOut = estimateTokens(assistantForTokens == null ? 0 : assistantForTokens.length());
            Integer prompt = (usage != null && usage.promptTokens() != null) ? usage.promptTokens() : estIn;
            Integer completion = (usage != null && usage.completionTokens() != null) ? usage.completionTokens() : estOut;
            Integer total = usageTotalOrFallback(usage, prompt + completion);
            usage = new LlmCallQueueService.UsageMetrics(prompt, completion, total, estOut);
        }
        if (shouldPreferTokenizerIn(taskType) && tokenCountService != null) {
            TokenCountService.TokenDecision dec = tokenCountService.decideChatTokens(
                    provider.id(),
                    model,
                    Boolean.TRUE.equals(enableThinking),
                    usage,
                    patchedMessages,
                    assistant,
                    true
            );
            if (dec != null) {
                Integer estOut = usage.estimatedCompletionTokens();
                usage = new LlmCallQueueService.UsageMetrics(dec.tokensIn(), dec.tokensOut(), dec.totalTokens(), estOut);
            }
        }
        return new ChatOnceInternalResult(raw, usage);
    }

    private LlmCallQueueService.UsageMetrics callChatStreamSingle(
            LlmQueueTaskType taskType,
            AiProvidersConfigService.ResolvedProvider provider,
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Boolean enableThinking,
            Integer thinkingBudget,
            OpenAiCompatClient.SseLineConsumer consumer,
            int streamAttempts,
            AtomicReference<String> taskIdOut
    ) throws Exception {
        OpenAiCompatClient client = new OpenAiCompatClient();
        Boolean enableThinkingToSend = shouldSendDashscopeThinking(provider) ? enableThinking : null;
        Integer thinkingBudgetToSend = shouldSendDashscopeThinking(provider) ? thinkingBudget : null;
        Map<String, Object> extraBodyToSend = filterExtraBody(provider, null);
        OpenAiCompatClient.ChatRequest req = new OpenAiCompatClient.ChatRequest(
                provider.apiKey(),
                provider.baseUrl(),
                model,
                messages,
                temperature,
                topP,
                null,
                null,
                enableThinkingToSend,
                thinkingBudgetToSend,
                extraBodyToSend,
                provider.extraHeaders(),
                provider.connectTimeoutMs(),
                provider.readTimeoutMs(),
                true
        );
        return llmCallQueueService.call(
                taskType,
                provider.id(),
                model,
                0,
                (task) -> {
                    if (taskIdOut != null) taskIdOut.set(task.id());
                    task.reportInput(buildChatInputDetail(provider.id(), model, true, messages, temperature, enableThinking, thinkingBudget));
                    AtomicReference<LlmCallQueueService.UsageMetrics> usageRef = new AtomicReference<>(null);
                    boolean stripThink = shouldStripThinkBlocks(enableThinking);
                    ThinkStripper stripper = stripThink ? new ThinkStripper() : null;
                    long[] outChars = new long[]{0L};
                    long[] effectiveChars = new long[]{0L};
                    StringBuilder outText = new StringBuilder();

                    OpenAiCompatClient.SseLineConsumer wrapped = (line) -> {
                        if (line != null) {
                            String t = line.trim();
                            if (t.startsWith("data:")) {
                                String payload = t.substring("data:".length()).trim();
                                if (!payload.isEmpty() && !payload.equals("[DONE]")) {
                                    extractStreamChunkStats(payload, !stripThink, outChars, usageRef);
                                    String delta = extractStreamChunkText(payload, !stripThink);
                                    if (delta != null && !delta.isEmpty()) {
                                        if (stripThink) {
                                            effectiveChars[0] += stripper.accept(delta, outText, 24000);
                                            task.reportEstimatedTokensOut(estimateTokens(effectiveChars[0]));
                                        } else {
                                            task.reportEstimatedTokensOut(estimateTokens(outChars[0]));
                                            if (outText.length() < 24000) {
                                                int remain = 24000 - outText.length();
                                                if (remain > 0) {
                                                    int take = Math.min(remain, delta.length());
                                                    outText.append(delta, 0, take);
                                                }
                                            }
                                        }
                                    } else if (!stripThink) {
                                        task.reportEstimatedTokensOut(estimateTokens(outChars[0]));
                                    }
                                }
                            }
                        }
                        consumer.onLine(line);
                    };

                    callStreamWithRetry(streamAttempts, req, client, wrapped);
                    task.reportOutput(outText.toString());

                    LlmCallQueueService.UsageMetrics u = usageRef.get();
                    int estTokensOut = estimateTokens(stripThink ? effectiveChars[0] : outChars[0]);
                    int estTokensIn = estimateInputTokens(messages);

                    Integer prompt = (u != null && u.promptTokens() != null) ? u.promptTokens() : estTokensIn;
                    Integer completion = (u != null && u.completionTokens() != null) ? u.completionTokens() : null;
                    Integer total = (u != null && u.totalTokens() != null) ? u.totalTokens() : null;

                    if (total == null) {
                        int out = (completion != null) ? completion : estTokensOut;
                        total = (prompt != null ? prompt : 0) + out;
                    }

                    return new LlmCallQueueService.UsageMetrics(prompt, completion, total, estTokensOut);
                },
                (m) -> m
        );
    }

    public RoutedChatStreamResult chatStreamRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Boolean enableThinking,
            Integer thinkingBudget,
            OpenAiCompatClient.SseLineConsumer consumer
    ) {
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.MULTIMODAL_CHAT : taskType;
        String pid = providerId == null ? null : providerId.trim();
        String mo = modelOverride == null ? null : modelOverride.trim();

        if (mo != null && !mo.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = mo;
            try {
                List<ChatMessage> patched = applyThinkingDirectiveToMessages(messages, enableThinking, model);
                LlmCallQueueService.UsageMetrics usage = callChatStreamSingle(tt, provider, model, patched, temperature, topP, enableThinking, thinkingBudget, consumer, 2, null);
                return new RoutedChatStreamResult(provider.id(), model, usage);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(upstreamStreamFailedPrefix() + ": " + e.getMessage(), e);
            }
        }

        if (pid != null && !pid.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = provider.defaultChatModel();
            try {
                List<ChatMessage> patched = applyThinkingDirectiveToMessages(messages, enableThinking, model);
                LlmCallQueueService.UsageMetrics usage = callChatStreamSingle(tt, provider, model, patched, temperature, topP, enableThinking, thinkingBudget, consumer, 2, null);
                return new RoutedChatStreamResult(provider.id(), model, usage);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(upstreamStreamFailedPrefix() + ": " + e.getMessage(), e);
            }
        }

        LlmRoutingService.Policy policy = llmRoutingService.getPolicy(tt);
        Set<LlmRoutingService.TargetId> tried = new java.util.HashSet<>();
        Exception last = null;

        for (int attempt = 1; attempt <= Math.max(1, policy.maxAttempts()); attempt++) {
            LlmRoutingService.RouteTarget target = llmRoutingService.pickNext(tt, tried);
            if (target == null) {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_NO_TARGET",
                        tt.name(),
                        attempt,
                        null,
                        null,
                        null,
                        false,
                        "",
                        "no eligible target",
                        0L,
                        "chatStream"
                ));
                break;
            }
            tried.add(target.id());

            AiProvidersConfigService.ResolvedProvider provider = resolve(target.providerId());
            String model = target.modelName();
            AtomicReference<String> taskIdRef = new AtomicReference<>(null);
            long startedNs = System.nanoTime();

            try {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_ATTEMPT",
                        tt.name(),
                        attempt,
                        null,
                        provider.id(),
                        model,
                        null,
                        "",
                        "",
                        null,
                        "chatStream"
                ));
                List<ChatMessage> patched = applyThinkingDirectiveToMessages(messages, enableThinking, model);
                LlmCallQueueService.UsageMetrics usage = callChatStreamSingle(tt, provider, model, patched, temperature, topP, enableThinking, thinkingBudget, consumer, 1, taskIdRef);
                llmRoutingService.recordSuccess(tt, target);
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_OK",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        provider.id(),
                        model,
                        true,
                        "",
                        "",
                        elapsedMs(startedNs),
                        "chatStream"
                ));
                return new RoutedChatStreamResult(provider.id(), model, usage);
            } catch (StreamCallFailedException e) {
                last = e;
                if (e.started()) {
                    throw new IllegalStateException(upstreamStreamFailedPrefix() + ": " + e.getMessage(), e);
                }
                Throwable cause = e.getCause();
                if (cause == null) {
                    cause = e;
                }
                if (!isRetriable(cause)) {
                    throw new IllegalStateException(upstreamStreamFailedPrefix() + ": " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(cause));
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_FAIL",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        provider.id(),
                        model,
                        false,
                        safeErrorCode(cause),
                        safeErrorMessage(cause),
                        elapsedMs(startedNs),
                        "chatStream"
                ));
            } catch (Exception e) {
                last = e;
                if (!isRetriable(e)) {
                    throw new IllegalStateException(upstreamStreamFailedPrefix() + ": " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(e));
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_FAIL",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        provider.id(),
                        model,
                        false,
                        safeErrorCode(e),
                        safeErrorMessage(e),
                        elapsedMs(startedNs),
                        "chatStream"
                ));
            }
        }

        try {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pickFallbackProviderId(AiProvidersConfigService.ResolvedProvider::defaultChatModel));
            String model = provider.defaultChatModel();
            AtomicReference<String> taskIdRef = new AtomicReference<>(null);
            long startedNs = System.nanoTime();
            llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                    System.currentTimeMillis(),
                    "FALLBACK_ATTEMPT",
                    tt.name(),
                    null,
                    null,
                    provider.id(),
                    model,
                    null,
                    "",
                    "",
                    null,
                    "chatStream"
            ));
            List<ChatMessage> patched = applyThinkingDirectiveToMessages(messages, enableThinking, model);
            LlmCallQueueService.UsageMetrics usage = callChatStreamSingle(tt, provider, model, patched, temperature, topP, enableThinking, thinkingBudget, consumer, 1, taskIdRef);
            llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                    System.currentTimeMillis(),
                    "FALLBACK_OK",
                    tt.name(),
                    null,
                    taskIdRef.get(),
                    provider.id(),
                    model,
                    true,
                    "",
                    "",
                    elapsedMs(startedNs),
                    "chatStream"
            ));
            return new RoutedChatStreamResult(provider.id(), model, usage);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Exception ex = last == null ? e : last;
            throw new IllegalStateException(upstreamStreamFailedPrefix() + ": " + ex.getMessage(), ex);
        }
    }

    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static void callStreamWithRetry(
            int maxAttempts,
            OpenAiCompatClient.ChatRequest req,
            OpenAiCompatClient client,
            OpenAiCompatClient.SseLineConsumer consumer
    ) throws Exception {
        int attempts = Math.max(1, maxAttempts);
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            AtomicBoolean started = new AtomicBoolean(false);
            OpenAiCompatClient.SseLineConsumer wrapped = (line) -> {
                if (!started.get() && line != null) {
                    String t = line.trim();
                    if (t.startsWith("data:") && !t.equals("data: [DONE]")) {
                        started.set(true);
                    }
                }
                consumer.onLine(line);
            };

            try {
                client.chatCompletionsStream(req, wrapped);
                return;
            } catch (Exception e) {
                last = e;
                if (started.get()) {
                    throw new StreamCallFailedException(e.getMessage(), e, true);
                }
                if (i >= attempts || !isRetriable(e)) {
                    throw new StreamCallFailedException(e.getMessage(), e, false);
                }
                sleepBackoff(i);
            }
        }
        if (last != null) throw new StreamCallFailedException(last.getMessage(), last, false);
    }

    private static void sleepBackoff(int attempt) throws InterruptedException {
        long base = 200L;
        long cap = 2000L;
        long exp = base * (1L << Math.min(4, Math.max(0, attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(0, 120);
        long sleepMs = Math.min(cap, exp) + jitter;
        Thread.sleep(sleepMs);
    }

    private static int estimateTokens(long chars) {
        return LlmGatewaySupport.estimateTokens(chars);
    }

    private static int estimateInputTokens(List<ChatMessage> messages) {
        return LlmGatewaySupport.estimateInputTokens(messages);
    }

    private static String sanitizeMarker(String s) {
        return LlmGatewaySupport.sanitizeMarker(s);
    }

    public RoutedChatOnceResult chatOnceRouted(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody,
            Map<String, String> extraRequestHeaders
    ) {
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.MULTIMODAL_CHAT : taskType;
        String pid = providerId == null ? null : providerId.trim();
        String mo = modelOverride == null ? null : modelOverride.trim();

        if (mo != null && !mo.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = mo;
            try {
                ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 3, null, extraRequestHeaders);
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + e.getMessage(), e);
            }
        }

        if (pid != null && !pid.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = provider.defaultChatModel();
            try {
                ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 3, null, extraRequestHeaders);
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + e.getMessage(), e);
            }
        }

        LlmRoutingService.Policy policy = llmRoutingService.getPolicy(tt);
        Set<LlmRoutingService.TargetId> tried = new java.util.HashSet<>();
        Exception last = null;

        for (int attempt = 1; attempt <= Math.max(1, policy.maxAttempts()); attempt++) {
            LlmRoutingService.RouteTarget target = llmRoutingService.pickNext(tt, tried);
            if (target == null) {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_NO_TARGET",
                        tt.name(),
                        attempt,
                        null,
                        null,
                        null,
                        false,
                        "",
                        "no eligible target",
                        0L,
                        "chatOnce"
                ));
                break;
            }
            tried.add(target.id());

            AiProvidersConfigService.ResolvedProvider provider = resolve(target.providerId());
            String model = target.modelName();
            AtomicReference<String> taskIdRef = new AtomicReference<>(null);
            long startedNs = System.nanoTime();
            try {
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_ATTEMPT",
                        tt.name(),
                        attempt,
                        null,
                        provider.id(),
                        model,
                        null,
                        "",
                        "",
                        null,
                        "chatOnce"
                ));
                ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 2, taskIdRef, extraRequestHeaders);
                llmRoutingService.recordSuccess(tt, target);
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_OK",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        provider.id(),
                        model,
                        true,
                        "",
                        "",
                        elapsedMs(startedNs),
                        "chatOnce"
                ));
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (Exception e) {
                last = e;
                if (!isRetriable(e)) {
                    throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(e));
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_FAIL",
                        tt.name(),
                        attempt,
                        taskIdRef.get(),
                        provider.id(),
                        model,
                        false,
                        safeErrorCode(e),
                        safeErrorMessage(e),
                        elapsedMs(startedNs),
                        "chatOnce"
                ));
            }
        }

        if (tt == LlmQueueTaskType.MULTIMODAL_CHAT || tt == LlmQueueTaskType.MULTIMODAL_MODERATION) {
            if (last != null) {
                throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + last.getMessage(), last);
            }
            throw new IllegalStateException("未配置可用的" + routeTargetLabel() + "：" + tt.name());
        }

        try {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pickFallbackProviderId(AiProvidersConfigService.ResolvedProvider::defaultChatModel));
            String model = provider.defaultChatModel();
            AtomicReference<String> taskIdRef = new AtomicReference<>(null);
            long startedNs = System.nanoTime();
            llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                    System.currentTimeMillis(),
                    "FALLBACK_ATTEMPT",
                    tt.name(),
                    null,
                    null,
                    provider.id(),
                    model,
                    null,
                    "",
                    "",
                    null,
                    "chatOnce"
            ));
            ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 2, taskIdRef, extraRequestHeaders);
            llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                    System.currentTimeMillis(),
                    "FALLBACK_OK",
                    tt.name(),
                    null,
                    taskIdRef.get(),
                    provider.id(),
                    model,
                    true,
                    "",
                    "",
                    elapsedMs(startedNs),
                    "chatOnce"
            ));
            return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Exception ex = last == null ? e : last;
            throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + ex.getMessage(), ex);
        }
    }

    public RoutedChatOnceResult chatOnceRoutedNoQueue(
            LlmQueueTaskType taskType,
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody,
            Map<String, String> extraRequestHeaders
    ) {
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.MULTIMODAL_CHAT : taskType;
        String pid = providerId == null ? null : providerId.trim();
        String mo = modelOverride == null ? null : modelOverride.trim();

        if (mo != null && !mo.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = mo;
            try {
                ChatOnceInternalResult res = callChatOnceSingleNoQueue(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 3, extraRequestHeaders);
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + e.getMessage(), e);
            }
        }

        if (pid != null && !pid.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = provider.defaultChatModel();
            try {
                ChatOnceInternalResult res = callChatOnceSingleNoQueue(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 3, extraRequestHeaders);
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + e.getMessage(), e);
            }
        }

        LlmRoutingService.Policy policy = llmRoutingService.getPolicy(tt);
        Set<LlmRoutingService.TargetId> tried = new java.util.HashSet<>();
        Exception last = null;

        for (int attempt = 1; attempt <= Math.max(1, policy.maxAttempts()); attempt++) {
            LlmRoutingService.RouteTarget target = llmRoutingService.pickNext(tt, tried);
            if (target == null) break;
            tried.add(target.id());

            AiProvidersConfigService.ResolvedProvider provider = resolve(target.providerId());
            String model = target.modelName();
            long startedNs = System.nanoTime();
            try {
                ChatOnceInternalResult res = callChatOnceSingleNoQueue(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 2, extraRequestHeaders);
                llmRoutingService.recordSuccess(tt, target);
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_OK_NOQUEUE",
                        tt.name(),
                        attempt,
                        null,
                        provider.id(),
                        model,
                        true,
                        "",
                        "",
                        elapsedMs(startedNs),
                        "chatOnceNoQueue"
                ));
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (Exception e) {
                last = e;
                if (!isRetriable(e)) {
                    throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(e));
                llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                        System.currentTimeMillis(),
                        "ROUTE_FAIL_NOQUEUE",
                        tt.name(),
                        attempt,
                        null,
                        provider.id(),
                        model,
                        false,
                        safeErrorCode(e),
                        safeErrorMessage(e),
                        elapsedMs(startedNs),
                        "chatOnceNoQueue"
                ));
            }
        }

        try {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pickFallbackProviderId(AiProvidersConfigService.ResolvedProvider::defaultChatModel));
            String model = provider.defaultChatModel();
            long startedNs = System.nanoTime();
            ChatOnceInternalResult res = callChatOnceSingleNoQueue(tt, provider, model, messages, temperature, topP, maxTokens, stop, enableThinking, thinkingBudget, extraBody, 2, extraRequestHeaders);
            llmRoutingTelemetryService.record(new LlmRoutingTelemetryService.RoutingDecisionEvent(
                    System.currentTimeMillis(),
                    "ROUTE_FALLBACK_NOQUEUE",
                    tt.name(),
                    null,
                    null,
                    provider.id(),
                    model,
                    true,
                    "",
                    "",
                    elapsedMs(startedNs),
                    "chatOnceNoQueue"
            ));
            return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            Exception ex = last == null ? e : last;
            throw new IllegalStateException(upstreamCallFailedPrefix() + ": " + ex.getMessage(), ex);
        }
    }

    private static boolean isUsageIncomplete(LlmCallQueueService.UsageMetrics usage) {
        return LlmGatewaySupport.isUsageIncomplete(usage);
    }

    private static Integer usageTotalOrFallback(LlmCallQueueService.UsageMetrics usage, Integer fallbackTotal) {
        return LlmGatewaySupport.usageTotalOrFallback(usage, fallbackTotal);
    }

    private void extractStreamChunkStats(
            String jsonPayload,
            boolean includeReasoning,
            long[] outChars,
            AtomicReference<LlmCallQueueService.UsageMetrics> usageRef
    ) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            if (root.isObject()) {
                JsonNode usage = root.path("usage");
                JsonNode usageNode = usage.isObject() ? usage : root;
                Integer prompt = pickIntLoose(usageNode, "prompt_tokens", "input_tokens", "promptTokens", "inputTokens");
                Integer completion = pickIntLoose(usageNode, "completion_tokens", "output_tokens", "completionTokens", "outputTokens");
                Integer total = pickIntLoose(usageNode, "total_tokens", "totalTokens");
                LlmCallQueueService.UsageMetrics norm = normalizeOpenAiCompatUsage(prompt, completion, total);
                if (norm != null) usageRef.set(norm);
            }
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode delta = first.path("delta");
                if (delta.isObject()) {
                    String reasoning = includeReasoning && delta.path("reasoning_content").isTextual() ? delta.path("reasoning_content").asText() : null;
                    if (reasoning != null && !reasoning.isEmpty()) outChars[0] += reasoning.length();
                    String content = delta.path("content").isTextual() ? delta.path("content").asText() : null;
                    if (content != null && !content.isEmpty()) outChars[0] += content.length();
                }
                String text = first.path("text").isTextual() ? first.path("text").asText() : null;
                if (text != null && !text.isEmpty()) outChars[0] += text.length();
            }
        } catch (Exception ignore) {
        }
    }

    private String extractStreamChunkText(String jsonPayload) {
        return extractStreamChunkText(jsonPayload, true);
    }

    private String extractStreamChunkText(String jsonPayload, boolean includeReasoning) {
        if (jsonPayload == null || jsonPayload.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            JsonNode choices = root.path("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode delta = first.path("delta");
                if (delta != null && delta.isObject()) {
                    String reasoning = includeReasoning && delta.path("reasoning_content").isTextual() ? delta.path("reasoning_content").asText() : null;
                    String content = delta.path("content").isTextual() ? delta.path("content").asText() : null;
                    
                    reasoning = sanitizeMarker(reasoning);
                    content = sanitizeMarker(content);
                    if (!includeReasoning) {
                        content = stripReasoningArtifacts(content);
                    }

                    if (reasoning != null && !reasoning.isEmpty() && content != null && !content.isEmpty()) return reasoning + content;
                    if (reasoning != null && !reasoning.isEmpty()) return reasoning;
                    if (content != null && !content.isEmpty()) return content;
                }
                String text = first.path("text").isTextual() ? first.path("text").asText() : null;
                if (text != null && !text.isEmpty()) return text;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private String buildChatInputDetail(
            String providerId,
            String model,
            boolean stream,
            List<ChatMessage> messages,
            Double temperature,
            Boolean enableThinking,
            Integer thinkingBudget
    ) {
        try {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("messages", sanitizeMessagesForTrace(messages));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
        } catch (Exception e) {
            return String.valueOf(messages);
        }
    }

    private List<ChatMessage> sanitizeMessagesForTrace(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return messages;
        List<ChatMessage> out = new java.util.ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg == null) continue;
            String role = msg.role();
            Object content = msg.content();
            if (content instanceof String s) {
                String t = stripTraceLines(s);
                t = removeLabelMapFromEmbeddedJson(t);
                out.add(new ChatMessage(role, t));
                continue;
            }
            if (content instanceof List<?> parts) {
                List<Object> outParts = new java.util.ArrayList<>();
                for (Object p : parts) {
                    if (p instanceof Map<?, ?> pm) {
                        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
                        for (Map.Entry<?, ?> e : pm.entrySet()) {
                            Object k = e.getKey();
                            if (!(k instanceof String ks)) continue;
                            Object v = e.getValue();
                            if ("text".equals(ks) && v instanceof String vs) {
                                String t = stripTraceLines(vs);
                                t = removeLabelMapFromEmbeddedJson(t);
                                copy.put(ks, t);
                            } else {
                                copy.put(ks, v);
                            }
                        }
                        outParts.add(copy);
                    } else {
                        outParts.add(p);
                    }
                }
                out.add(new ChatMessage(role, outParts));
                continue;
            }
            out.add(msg);
        }
        return out;
    }

    private String stripTraceLines(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String[] lines = raw.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder(raw.length());
        boolean wrote = false;
        for (String line : lines) {
            if (line != null && line.startsWith("TRACE ")) continue;
            if (wrote) sb.append('\n');
            sb.append(line == null ? "" : line);
            wrote = true;
        }
        return sb.toString().trim();
    }

    private String removeLabelMapFromEmbeddedJson(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        int idxObj = raw.lastIndexOf("\n\n{");
        int idxArr = raw.lastIndexOf("\n\n[");
        int idx = Math.max(idxObj, idxArr);
        if (idx < 0) {
            idxObj = raw.lastIndexOf("\n{");
            idxArr = raw.lastIndexOf("\n[");
            idx = Math.max(idxObj, idxArr);
        }
        if (idx < 0) return raw;
        int startObj = raw.indexOf('{', idx);
        int startArr = raw.indexOf('[', idx);
        int start;
        if (startObj < 0) start = startArr;
        else if (startArr < 0) start = startObj;
        else start = Math.min(startObj, startArr);
        if (start < 0) return raw;

        String prefix = raw.substring(0, start).trim();
        String json = raw.substring(start).trim();
        if (json.isBlank()) return raw;
        try {
            com.fasterxml.jackson.databind.JsonNode n = objectMapper.readTree(json);
            if (n == null) return raw;
            if (n.isObject()) {
                com.fasterxml.jackson.databind.node.ObjectNode o = (com.fasterxml.jackson.databind.node.ObjectNode) n;
                com.fasterxml.jackson.databind.JsonNode lt = o.get("label_taxonomy");
                if (lt != null && lt.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) lt).remove("label_map");
                String outJson = objectMapper.writeValueAsString(o);
                if (prefix.isBlank()) return outJson;
                return (prefix + "\n\n" + outJson).trim();
            }
            if (n.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode it : n) {
                    if (it != null && it.isObject()) {
                        com.fasterxml.jackson.databind.JsonNode lt = it.get("label_taxonomy");
                        if (lt != null && lt.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) lt).remove("label_map");
                    }
                }
                String outJson = objectMapper.writeValueAsString(n);
                if (prefix.isBlank()) return outJson;
                return (prefix + "\n\n" + outJson).trim();
            }
        } catch (Exception ignore) {
        }
        return raw;
    }

    private static final class StreamCallFailedException extends Exception {
        private final boolean started;

        private StreamCallFailedException(String message, Throwable cause, boolean started) {
            super(message, cause);
            this.started = started;
        }

        public boolean started() {
            return started;
        }
    }

    public AiProvidersConfigService.ResolvedProvider resolve(String providerId) {
        String id = providerId == null ? null : providerId.trim();
        AiProvidersConfigService.ResolvedProvider provider = (id == null || id.isBlank())
                ? aiProvidersConfigService.resolveActiveProvider()
                : aiProvidersConfigService.resolveProvider(id);

        if (provider == null) {
            provider = aiProvidersConfigService.resolveActiveProvider();
        }
        if (provider == null) {
            throw new IllegalStateException("未配置任何有效的 AI 模型提供商(Provider)，请在系统管理中配置。");
        }

        String type = provider.type() == null ? "OPENAI_COMPAT" : provider.type().trim().toUpperCase(Locale.ROOT);
        if (!"OPENAI_COMPAT".equals(type) && !"LOCAL_OPENAI_COMPAT".equals(type)) {
            throw new IllegalStateException(unsupportedProviderTypePrefix() + ": " + type);
        }
        return provider;
    }

    private String extractAssistantContent(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (contentNode.isTextual()) return sanitizeMarker(contentNode.asText());
                JsonNode textNode = first.path("text");
                if (textNode.isTextual()) return sanitizeMarker(textNode.asText());
            }
        } catch (Exception ignore) {
        }
        return rawJson;
    }

    private ChatOnceInternalResult callChatOnceSingle(
            LlmQueueTaskType taskType,
            AiProvidersConfigService.ResolvedProvider provider,
            String model,
            List<ChatMessage> messages,
            Double temperature,
            Double topP,
            Integer maxTokens,
            List<String> stop,
            Boolean enableThinking,
            Integer thinkingBudget,
            Map<String, Object> extraBody,
            int httpAttempts,
            AtomicReference<String> taskIdOut,
            Map<String, String> extraRequestHeaders
    ) throws Exception {
        OpenAiCompatClient client = new OpenAiCompatClient();
        List<ChatMessage> patchedMessages = applyThinkingDirectiveToMessages(messages, enableThinking, model);
        boolean stripThink = shouldStripThinkBlocks(enableThinking);
        boolean isDashscope = shouldSendDashscopeThinking(provider);
        Boolean enableThinkingToSend = isDashscope ? Boolean.FALSE : null;
        Integer thinkingBudgetToSend = null;
        Map<String, Object> extraBodyToSend = filterExtraBody(provider, extraBody);
        OpenAiCompatClient.ChatRequest req = new OpenAiCompatClient.ChatRequest(
                provider.apiKey(),
                provider.baseUrl(),
                model,
                patchedMessages,
                temperature,
                topP,
                maxTokens,
                stop,
                enableThinkingToSend,
                thinkingBudgetToSend,
                extraBodyToSend,
                mergeHeaders(provider.extraHeaders(), extraRequestHeaders),
                provider.connectTimeoutMs(),
                provider.readTimeoutMs(),
                false
        );
        String text = llmCallQueueService.call(
                taskType,
                provider.id(),
                model,
                0,
                (task) -> {
                    if (taskIdOut != null) taskIdOut.set(task.id());
                    task.reportInput(buildChatInputDetail(provider.id(), model, false, patchedMessages, temperature, enableThinking, thinkingBudget));
                    String raw = withRetry(Math.max(1, httpAttempts), () -> client.chatCompletionsOnce(req));
                    String assistantRaw = extractAssistantContent(raw);
                    String assistant = stripThink
                            ? stripReasoningArtifacts(stripThinkBlocks(assistantRaw))
                            : assistantRaw;
                    if (stripThink) {
                        task.reportOutput(assistant);
                    } else if (raw != null && !raw.isBlank() && !assistant.equals(raw)) {
                        task.reportOutput(outputTextLabel() + "\n" + assistant + "\n\n原始响应:\n" + raw);
                    } else {
                        task.reportOutput(assistant);
                    }
                    return raw;
                },
                (raw) -> {
                    LlmCallQueueService.UsageMetrics usage = llmCallQueueService.parseOpenAiUsageFromJson(raw);
                    if (isUsageIncomplete(usage)) {
                        int estIn = estimateInputTokens(patchedMessages);
                        String assistantForTokens = extractAssistantContent(raw);
                        if (stripThink) {
                            assistantForTokens = stripReasoningArtifacts(stripThinkBlocks(assistantForTokens));
                        }
                        int estOut = estimateTokens(assistantForTokens.length());
                        Integer prompt = (usage != null && usage.promptTokens() != null) ? usage.promptTokens() : estIn;
                        Integer completion = (usage != null && usage.completionTokens() != null) ? usage.completionTokens() : estOut;
                        Integer total = usageTotalOrFallback(usage, prompt + completion);
                        usage = new LlmCallQueueService.UsageMetrics(prompt, completion, total, estOut);
                    }
                    if (shouldPreferTokenizerIn(taskType) && tokenCountService != null) {
                        String assistantForTokens = extractAssistantContent(raw);
                        if (stripThink) {
                            assistantForTokens = stripReasoningArtifacts(stripThinkBlocks(assistantForTokens));
                        }
                        TokenCountService.TokenDecision dec = tokenCountService.decideChatTokens(
                                provider.id(),
                                model,
                                Boolean.TRUE.equals(enableThinking),
                                usage,
                                patchedMessages,
                                assistantForTokens,
                                true
                        );
                        if (dec != null) {
                            Integer estOut = usage.estimatedCompletionTokens();
                            usage = new LlmCallQueueService.UsageMetrics(dec.tokensIn(), dec.tokensOut(), dec.totalTokens(), estOut);
                        }
                    }
                    return usage;
                }
        );

        LlmCallQueueService.UsageMetrics usage = llmCallQueueService.parseOpenAiUsageFromJson(text);
        if (isUsageIncomplete(usage)) {
            int estIn = estimateInputTokens(patchedMessages);
            String assistantForTokens = extractAssistantContent(text);
            if (stripThink) {
                assistantForTokens = stripReasoningArtifacts(stripThinkBlocks(assistantForTokens));
            }
            int estOut = estimateTokens(assistantForTokens.length());

            Integer prompt = (usage != null && usage.promptTokens() != null) ? usage.promptTokens() : estIn;
            Integer completion = (usage != null && usage.completionTokens() != null) ? usage.completionTokens() : estOut;
            Integer total = usageTotalOrFallback(usage, prompt + completion);
            usage = new LlmCallQueueService.UsageMetrics(prompt, completion, total, estOut);
        }
        if (shouldPreferTokenizerIn(taskType) && tokenCountService != null) {
            String assistantForTokens = extractAssistantContent(text);
            if (stripThink) {
                assistantForTokens = stripReasoningArtifacts(stripThinkBlocks(assistantForTokens));
            }
            TokenCountService.TokenDecision dec = tokenCountService.decideChatTokens(
                    provider.id(),
                    model,
                    Boolean.TRUE.equals(enableThinking),
                    usage,
                    patchedMessages,
                    assistantForTokens,
                    true
            );
            if (dec != null) {
                Integer estOut = usage == null ? null : usage.estimatedCompletionTokens();
                usage = new LlmCallQueueService.UsageMetrics(dec.tokensIn(), dec.tokensOut(), dec.totalTokens(), estOut);
            }
        }

        return new ChatOnceInternalResult(text, usage);
    }
}
