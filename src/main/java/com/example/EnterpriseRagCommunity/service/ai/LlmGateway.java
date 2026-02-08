package com.example.EnterpriseRagCommunity.service.ai;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.service.ai.client.OpenAiCompatClient;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlmGateway {

    private final AiProvidersConfigService aiProvidersConfigService;
    private final AiProperties aiProperties;
    private final AiEmbeddingService aiEmbeddingService;
    private final AiRerankService aiRerankService;
    private final LlmCallQueueService llmCallQueueService;
    private final LlmRoutingService llmRoutingService;
    private final LlmRoutingTelemetryService llmRoutingTelemetryService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger rrCounter = new AtomicInteger(0);

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
        return chatOnce(LlmQueueTaskType.TEXT_CHAT, providerId, modelOverride, messages, temperature);
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
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.TEXT_CHAT : taskType;
        String pid = providerId == null ? null : providerId.trim();
        String mo = modelOverride == null ? null : modelOverride.trim();

        if (mo != null && !mo.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = mo;
            try {
                ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, null, null, 3, null);
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
            }
        }

        if (pid != null && !pid.isBlank()) {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pid);
            String model = provider.defaultChatModel();
            try {
                ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, null, null, 3, null);
                return new RoutedChatOnceResult(res.text(), provider.id(), model, res.usage());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
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
                ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, null, null, 2, taskIdRef);
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
                    throw new IllegalStateException("上游AI调用失败: " + e.getMessage(), e);
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

        if (tt == LlmQueueTaskType.IMAGE_CHAT || tt == LlmQueueTaskType.IMAGE_MODERATION) {
            if (last != null) {
                throw new IllegalStateException("上游AI调用失败: " + last.getMessage(), last);
            }
            throw new IllegalStateException("未配置可用的路由目标：" + tt.name());
        }

        try {
            AiProvidersConfigService.ResolvedProvider provider = resolve(pickNextEnabledProviderIdRoundRobin());
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
            ChatOnceInternalResult res = callChatOnceSingle(tt, provider, model, messages, temperature, topP, maxTokens, stop, null, null, 2, taskIdRef);
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
            throw new IllegalStateException("上游AI调用失败: " + ex.getMessage(), ex);
        }
    }

    public void chatStream(
            String providerId,
            String modelOverride,
            List<ChatMessage> messages,
            Double temperature,
            OpenAiCompatClient.SseLineConsumer consumer
    ) {
        chatStream(LlmQueueTaskType.TEXT_CHAT, providerId, modelOverride, messages, temperature, null, null, consumer);
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
        LlmQueueTaskType tt = taskType == null ? LlmQueueTaskType.TEXT_CHAT : taskType;
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
                throw new IllegalStateException("上游AI流式调用失败: " + e.getMessage(), e);
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
                throw new IllegalStateException("上游AI流式调用失败: " + e.getMessage(), e);
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
                    throw new IllegalStateException("上游AI流式调用失败: " + e.getMessage(), e);
                }
                if (!isRetriable(e.getCause() == null ? e : e.getCause())) {
                    throw new IllegalStateException("上游AI流式调用失败: " + e.getMessage(), e);
                }
                llmRoutingService.recordFailure(tt, target, extractErrorCode(e.getCause() == null ? e : e.getCause()));
                Throwable cause = e.getCause() == null ? e : e.getCause();
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
                    throw new IllegalStateException("上游AI流式调用失败: " + e.getMessage(), e);
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
            AiProvidersConfigService.ResolvedProvider provider = resolve(pickNextEnabledProviderIdRoundRobin());
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
            throw new IllegalStateException("上游AI流式调用失败: " + ex.getMessage(), ex);
        }
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
        if (pid != null && !pid.isBlank()) {
            return aiEmbeddingService.embedOnceForTask(input, null, pid, tt);
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
        throw new IOException("Embedding failed: no eligible upstream target");
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

    public AiProvidersConfigService.ResolvedProvider resolve(String providerId) {
        String id = providerId == null ? null : providerId.trim();
        AiProvidersConfigService.ResolvedProvider provider = (id == null || id.isBlank())
                ? aiProvidersConfigService.resolveActiveProvider()
                : aiProvidersConfigService.resolveProvider(id);

        if (provider == null) {
            provider = aiProvidersConfigService.resolveActiveProvider();
        }
        if (provider == null) {
            provider = new AiProvidersConfigService.ResolvedProvider(
                    "default",
                    "OPENAI_COMPAT",
                    aiProperties.getBaseUrl(),
                    aiProperties.getApiKey(),
                    aiProperties.getModel(),
                    "text-embedding-v4",
                    Map.<String, Object>of(),
                    Map.<String, String>of(),
                    aiProperties.getConnectTimeoutMs(),
                    aiProperties.getReadTimeoutMs(),
                    null
            );
        }

        String type = provider.type() == null ? "OPENAI_COMPAT" : provider.type().trim().toUpperCase(Locale.ROOT);
        if (!"OPENAI_COMPAT".equals(type) && !"LOCAL_OPENAI_COMPAT".equals(type)) {
            throw new IllegalStateException("暂不支持的模型来源类型: " + type);
        }
        return provider;
    }

    private String pickNextEnabledProviderIdRoundRobin() {
        List<String> ids = aiProvidersConfigService.listEnabledProviderIds();
        if (ids == null || ids.isEmpty()) return null;
        int idx = Math.floorMod(rrCounter.getAndIncrement(), ids.size());
        String id = ids.get(idx);
        return id == null || id.isBlank() ? null : id.trim();
    }

    private record ChatOnceInternalResult(String text, LlmCallQueueService.UsageMetrics usage) {}

    private static boolean shouldStripThinkBlocks(Boolean enableThinking) {
        return Boolean.FALSE.equals(enableThinking);
    }

    private static boolean supportsThinkingDirectiveModel(String modelName) {
        String raw = modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) return false;

        String base = raw;
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < base.length()) base = base.substring(slash + 1);
        int colon = base.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < base.length()) base = base.substring(colon + 1);

        if (raw.contains("thinking") || base.contains("thinking")) return false;
        if (base.startsWith("qwen3-") || raw.startsWith("qwen3-")) return true;
        return base.startsWith("qwen-plus-2025-04-28")
                || base.startsWith("qwen-turbo-2025-04-28")
                || raw.startsWith("qwen-plus-2025-04-28")
                || raw.startsWith("qwen-turbo-2025-04-28");
    }

    private static String applyThinkingDirective(String content, boolean enableThinking, String modelName) {
        String text = content == null ? "" : content;
        if (!supportsThinkingDirectiveModel(modelName)) return text;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("/no_think") || lower.contains("/think")) return text;
        String directive = enableThinking ? "/think" : "/no_think";
        if (text.endsWith("\n") || text.endsWith("\r")) return text + directive;
        return text + "\n" + directive;
    }

    private static List<ChatMessage> applyThinkingDirectiveToMessages(List<ChatMessage> messages, Boolean enableThinking, String modelName) {
        if (messages == null || messages.isEmpty()) return messages;
        if (enableThinking == null) return messages;
        if (!supportsThinkingDirectiveModel(modelName)) return messages;

        int lastUserIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m == null) continue;
            String role = m.role();
            if (role == null) continue;
            if ("user".equalsIgnoreCase(role)) {
                lastUserIndex = i;
                break;
            }
        }
        if (lastUserIndex < 0) return messages;
        ChatMessage lastUser = messages.get(lastUserIndex);
        Object contentObj = lastUser == null ? null : lastUser.content();
        if (!(contentObj instanceof String content)) return messages;

        String patched = applyThinkingDirective(content, Boolean.TRUE.equals(enableThinking), modelName);
        if (patched.equals(content)) return messages;

        java.util.ArrayList<ChatMessage> next = new java.util.ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage m = messages.get(i);
            if (i == lastUserIndex && m != null) {
                next.add(new ChatMessage(m.role(), patched));
            } else {
                next.add(m);
            }
        }
        return java.util.Collections.unmodifiableList(next);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
        if (haystack == null || needle == null) return -1;
        int start = Math.max(0, fromIndex);
        if (needle.isEmpty()) return start <= haystack.length() ? start : -1;
        int n = haystack.length();
        int m = needle.length();
        if (m > n) return -1;
        for (int i = start; i + m <= n; i++) {
            boolean ok = true;
            for (int j = 0; j < m; j++) {
                char a = haystack.charAt(i + j);
                char b = needle.charAt(j);
                if (a == b) continue;
                if (Character.toLowerCase(a) != Character.toLowerCase(b)) {
                    ok = false;
                    break;
                }
            }
            if (ok) return i;
        }
        return -1;
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private static String stripThinkBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeClosedThinkBlocks(text);
        int start = minPositive(
                indexOfIgnoreCase(t, "<think", 0),
                indexOfIgnoreCase(t, "&lt;think", 0)
        );
        if (start >= 0) return t.substring(0, start);
        return t;
    }

    private static String removeMarkerWordIgnoreCase(String text, String marker) {
        if (text == null || text.isBlank()) return text;
        if (marker == null || marker.isBlank()) return text;
        String t = text;
        String m = marker.trim();
        int first = indexOfIgnoreCase(t, m, 0);
        if (first < 0) return t;
        StringBuilder sb = new StringBuilder(t.length());
        int i = 0;
        while (true) {
            int idx = indexOfIgnoreCase(t, m, i);
            if (idx < 0) {
                sb.append(t, i, t.length());
                break;
            }
            sb.append(t, i, idx);
            i = idx + m.length();
        }
        return sb.toString();
    }

    private static String removeClosedReasoningBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text;
        for (int guard = 0; guard < 50; guard++) {
            int s1 = indexOfIgnoreCase(t, "<reasoning_content", 0);
            int s2 = indexOfIgnoreCase(t, "&lt;reasoning_content", 0);
            int start = minPositive(s1, s2);
            if (start < 0) return t;
            boolean escaped = s2 >= 0 && start == s2;

            int openEndExclusive;
            if (escaped) {
                int gt = indexOfIgnoreCase(t, "&gt;", start);
                if (gt < 0) return t;
                openEndExclusive = gt + "&gt;".length();
            } else {
                int gt = t.indexOf('>', start);
                if (gt < 0) return t;
                openEndExclusive = gt + 1;
            }

            int closeStart = escaped
                    ? indexOfIgnoreCase(t, "&lt;/reasoning_content&gt;", openEndExclusive)
                    : indexOfIgnoreCase(t, "</reasoning_content>", openEndExclusive);
            if (closeStart < 0) return t;
            int closeEndExclusive = closeStart + (escaped ? "&lt;/reasoning_content&gt;".length() : "</reasoning_content>".length());

            int after = closeEndExclusive;
            while (after < t.length() && Character.isWhitespace(t.charAt(after))) after++;
            t = t.substring(0, start) + t.substring(after);
        }
        return t;
    }

    private static String stripReasoningArtifacts(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeClosedReasoningBlocks(text);
        t = removeMarkerWordIgnoreCase(t, "reasoning_content");
        return t;
    }

    private static String removeClosedThinkBlocks(String text) {
        if (text == null || text.isBlank()) return text;
        String t = text;
        for (int guard = 0; guard < 50; guard++) {
            int s1 = indexOfIgnoreCase(t, "<think", 0);
            int s2 = indexOfIgnoreCase(t, "&lt;think", 0);
            int start = minPositive(s1, s2);
            if (start < 0) return t;
            boolean escaped = s2 >= 0 && start == s2;

            int openEndExclusive;
            if (escaped) {
                int gt = indexOfIgnoreCase(t, "&gt;", start);
                if (gt < 0) return t;
                openEndExclusive = gt + "&gt;".length();
            } else {
                int gt = t.indexOf('>', start);
                if (gt < 0) return t;
                openEndExclusive = gt + 1;
            }

            int closeStart = escaped
                    ? indexOfIgnoreCase(t, "&lt;/think&gt;", openEndExclusive)
                    : indexOfIgnoreCase(t, "</think>", openEndExclusive);
            if (closeStart < 0) return t;
            int closeEndExclusive = closeStart + (escaped ? "&lt;/think&gt;".length() : "</think>".length());

            int after = closeEndExclusive;
            while (after < t.length() && Character.isWhitespace(t.charAt(after))) after++;
            t = t.substring(0, start) + t.substring(after);
        }
        return t;
    }

    private static final class ThinkStripper {
        private boolean inThink = false;
        private String carry = "";

        private long appendLimited(StringBuilder out, String seg, int maxChars) {
            if (seg == null || seg.isEmpty()) return 0L;
            long produced = seg.length();
            if (out != null && out.length() < maxChars) {
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
                    boolean raw = isRawStartAt(input, start);
                    if (!escaped && !raw) {
                        produced += appendLimited(out, input.substring(start, Math.min(input.length(), start + 1)), maxChars);
                        i = start + 1;
                        continue;
                    }

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
            int httpAttempts,
            AtomicReference<String> taskIdOut
    ) throws Exception {
        OpenAiCompatClient client = new OpenAiCompatClient(aiProperties);
        Boolean enableThinkingToSend = shouldSendDashscopeThinking(provider) ? enableThinking : null;
        Integer thinkingBudgetToSend = shouldSendDashscopeThinking(provider) ? thinkingBudget : null;
        OpenAiCompatClient.ChatRequest req = new OpenAiCompatClient.ChatRequest(
                provider.apiKey(),
                provider.baseUrl(),
                model,
                messages,
                temperature,
                topP,
                maxTokens,
                stop,
                enableThinkingToSend,
                thinkingBudgetToSend,
                provider.extraHeaders(),
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
                    task.reportInput(buildChatInputDetail(provider.id(), model, false, messages, temperature, enableThinking, thinkingBudget));
                    String raw = withRetry(Math.max(1, httpAttempts), () -> client.chatCompletionsOnce(req));
                    String assistantRaw = extractAssistantContent(raw);
                    String assistant = shouldStripThinkBlocks(enableThinking)
                            ? stripReasoningArtifacts(stripThinkBlocks(assistantRaw))
                            : assistantRaw;
                    if (raw != null && !raw.isBlank() && assistant != null && !assistant.equals(raw)) {
                        task.reportOutput("输出文本:\n" + assistant + "\n\n原始响应:\n" + raw);
                    } else {
                        task.reportOutput(assistant == null ? "" : assistant);
                    }
                    return raw;
                },
                llmCallQueueService::parseOpenAiUsageFromJson
        );
        
        LlmCallQueueService.UsageMetrics usage = llmCallQueueService.parseOpenAiUsageFromJson(text);
        if (usage == null || usage.promptTokens() == null || usage.totalTokens() == null || usage.completionTokens() == null) {
            int estIn = estimateInputTokens(messages);
            String assistantForTokens = extractAssistantContent(text);
            if (shouldStripThinkBlocks(enableThinking)) {
                assistantForTokens = stripReasoningArtifacts(stripThinkBlocks(assistantForTokens));
            }
            int estOut = estimateTokens(assistantForTokens == null ? 0 : assistantForTokens.length());
            
            Integer prompt = (usage != null && usage.promptTokens() != null) ? usage.promptTokens() : estIn;
            Integer completion = (usage != null && usage.completionTokens() != null) ? usage.completionTokens() : estOut;
            Integer total = (usage != null && usage.totalTokens() != null) ? usage.totalTokens() : (prompt + completion);
            usage = new LlmCallQueueService.UsageMetrics(prompt, completion, total, estOut);
        }
        
        return new ChatOnceInternalResult(text, usage);
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
        OpenAiCompatClient client = new OpenAiCompatClient(aiProperties);
        Boolean enableThinkingToSend = shouldSendDashscopeThinking(provider) ? enableThinking : null;
        Integer thinkingBudgetToSend = shouldSendDashscopeThinking(provider) ? thinkingBudget : null;
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

    private static long elapsedMs(long startedNs) {
        long ns = System.nanoTime() - startedNs;
        if (ns <= 0) return 0L;
        return ns / 1_000_000L;
    }

    private static String safeErrorCode(Throwable e) {
        if (e == null) return "";
        String c = extractErrorCode(e);
        return c == null ? "" : c;
    }

    private static String safeErrorMessage(Throwable e) {
        if (e == null) return "";
        String m = e.getMessage();
        if (m == null) return "";
        String t = m.trim();
        if (t.length() <= 500) return t;
        return t.substring(0, 500);
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

    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private boolean shouldSendDashscopeThinking(AiProvidersConfigService.ResolvedProvider provider) {
        String baseUrl = provider == null ? null : provider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) return false;
        String u = baseUrl.trim().toLowerCase(Locale.ROOT);
        return u.contains("dashscope.aliyuncs.com") || u.contains("dashscope-intl.aliyuncs.com");
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
        if (last != null) throw last;
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

    private static boolean isRetriable(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) return true;
            if (cur instanceof ConnectException) return true;
            if (cur instanceof UnknownHostException) return true;
            if (cur instanceof IOException) {
                String msg = cur.getMessage();
                if (msg != null) {
                    if (msg.contains("HTTP 429")) return true;
                    if (msg.contains("HTTP 5")) return true;
                    if (msg.contains("Connection reset")) return true;
                    if (msg.contains("timed out")) return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static String extractErrorCode(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (msg.contains("HTTP 429")) return "429";
                if (msg.contains("HTTP 5")) return "5xx";
                if (msg.toLowerCase(Locale.ROOT).contains("rate limit")) return "429";
                if (msg.toLowerCase(Locale.ROOT).contains("too many requests")) return "429";
                if (msg.contains("Connection reset")) return "reset";
                if (msg.toLowerCase(Locale.ROOT).contains("timed out")) return "timeout";
            }
            if (cur instanceof SocketTimeoutException) return "timeout";
            if (cur instanceof ConnectException) return "connect";
            if (cur instanceof UnknownHostException) return "dns";
            cur = cur.getCause();
        }
        return "";
    }

    private static Integer asIntLoose(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        if (n.isNumber()) return n.asInt();
        if (n.isTextual()) {
            String s = n.asText();
            if (s == null) return null;
            String t = s.trim();
            if (t.isEmpty()) return null;
            try {
                return Integer.parseInt(t);
            } catch (Exception ignore) {
            }
            try {
                return (int) Double.parseDouble(t);
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static Integer pickIntLoose(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject() || keys == null) return null;
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            Integer v = asIntLoose(obj.path(k));
            if (v != null) return v;
        }
        return null;
    }

    private static LlmCallQueueService.UsageMetrics normalizeOpenAiCompatUsage(Integer prompt, Integer completion, Integer total) {
        Integer p = prompt;
        Integer c = completion;
        Integer t = total;
        if (p != null && p < 0) p = null;
        if (c != null && c < 0) c = null;
        if (t != null && t < 0) t = null;

        if (p != null && c != null && t != null) {
            if (t < p) {
                c = t;
                t = p + c;
            } else if ((c == null || c <= 0) && (t - p) > 0) {
                c = t - p;
            } else {
                t = p + c;
            }
        } else if (p != null && c != null) {
            t = p + c;
        } else if (p != null && c == null && t != null) {
            if (t >= p) {
                c = t - p;
            } else {
                c = t;
                t = p + c;
            }
        } else if (p == null && c != null && t != null) {
            if (t >= c) p = t - c;
        }

        if (p == null && c == null && t == null) return null;
        return new LlmCallQueueService.UsageMetrics(p, c, t, null);
    }

    private void extractStreamChunkStats(
            String jsonPayload,
            boolean includeReasoning,
            long[] outChars,
            AtomicReference<LlmCallQueueService.UsageMetrics> usageRef
    ) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            if (root != null && root.isObject()) {
                JsonNode usage = root.path("usage");
                JsonNode usageNode = usage != null && usage.isObject() ? usage : root;
                Integer prompt = pickIntLoose(usageNode, "prompt_tokens", "input_tokens", "promptTokens", "inputTokens");
                Integer completion = pickIntLoose(usageNode, "completion_tokens", "output_tokens", "completionTokens", "outputTokens");
                Integer total = pickIntLoose(usageNode, "total_tokens", "totalTokens");
                LlmCallQueueService.UsageMetrics norm = normalizeOpenAiCompatUsage(prompt, completion, total);
                if (norm != null) usageRef.set(norm);
            }
            JsonNode choices = root.path("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode delta = first.path("delta");
                if (delta != null && delta.isObject()) {
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
            m.put("providerId", providerId);
            m.put("model", model);
            m.put("stream", stream);
            m.put("temperature", temperature);
            m.put("enableThinking", enableThinking);
            m.put("thinkingBudget", thinkingBudget);
            m.put("messages", messages);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(m);
        } catch (Exception e) {
            return String.valueOf(messages);
        }
    }

    private static int estimateTokens(long chars) {
        if (chars <= 0) return 0;
        return (int) Math.max(1, (chars + 3) / 4);
    }

    private static int estimateInputTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        long chars = 0;
        for (ChatMessage m : messages) {
            if (m == null) continue;
            String role = m.role();
            Object content = m.content();
            if (role != null) chars += role.length();
            if (content == null) continue;
            if (content instanceof String s) {
                chars += s.length();
                continue;
            }
            if (content instanceof List<?> parts) {
                for (Object p : parts) {
                    if (p == null) continue;
                    if (p instanceof Map<?, ?> pm) {
                        Object t = pm.get("text");
                        if (t instanceof String ts) chars += ts.length();
                        Object iu = pm.get("image_url");
                        if (iu instanceof Map<?, ?> ium) {
                            Object u = ium.get("url");
                            if (u instanceof String us) chars += us.length();
                        }
                    } else {
                        chars += String.valueOf(p).length();
                    }
                }
                continue;
            }
            chars += String.valueOf(content).length();
        }
        return estimateTokens(chars);
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

    private static String sanitizeMarker(String s) {
        if (s == null) return null;
        String t = s;
        if (t.equals("reasoning_content")) return "";
        String trimmed = t.trim();
        if (trimmed.isEmpty()) return t;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("reasoning_content")) return "";
        if (lower.equals("<reasoning_content>") || lower.equals("</reasoning_content>")) return "";
        if (lower.equals("&lt;reasoning_content&gt;") || lower.equals("&lt;/reasoning_content&gt;")) return "";
        if (lower.startsWith("reasoning_content") && removeMarkerWordIgnoreCase(trimmed, "reasoning_content").trim().isEmpty()) return "";
        return t;
    }
}
