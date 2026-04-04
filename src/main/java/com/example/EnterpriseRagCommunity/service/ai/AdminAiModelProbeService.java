package com.example.EnterpriseRagCommunity.service.ai;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.ai.AdminAiModelProbeResultDTO;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAiModelProbeService {

    private final LlmGateway llmGateway;
    private final AiEmbeddingService aiEmbeddingService;
    private final AiRerankService aiRerankService;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public AdminAiModelProbeResultDTO probe(String kindRaw, String providerIdRaw, String modelNameRaw, Long timeoutMsRaw) {
        String kind = normalizeKind(kindRaw);
        String providerId = trimOrEmpty(providerIdRaw);
        String modelName = trimOrEmpty(modelNameRaw);
        long timeoutMs = timeoutMsRaw == null ? 15000L : Math.max(1000L, timeoutMsRaw.longValue());

        if (kind.isBlank()) throw new IllegalArgumentException("kind 不能为空");
        if (providerId.isBlank()) throw new IllegalArgumentException("providerId 不能为空");
        if (modelName.isBlank()) throw new IllegalArgumentException("modelName 不能为空");

        long startedAt = System.currentTimeMillis();
        AdminAiModelProbeResultDTO out = new AdminAiModelProbeResultDTO();
        out.setProviderId(providerId);
        out.setModelName(modelName);
        out.setKind(kind);

        try {
            if ("EMBEDDING".equals(kind)) {
                AiEmbeddingService.EmbeddingResult res = withTimeout(
                        () -> aiEmbeddingService.embedOnce("ping", modelName, providerId),
                        timeoutMs
                );
                boolean ok = res != null && res.vector() != null && res.vector().length > 0;
                out.setOk(ok);
                out.setUsedProviderId(providerId);
                out.setUsedModel(res == null ? modelName : Objects.requireNonNullElse(res.model(), modelName));
                if (!ok) out.setErrorMessage("embedding 响应为空或向量为空");
            } else if ("RERANK".equals(kind)) {
                AiRerankService.RerankResult res = withTimeout(
                        () -> aiRerankService.rerankOnce(
                                providerId,
                                modelName,
                                "ping",
                                List.of("ping"),
                                1,
                                "Given a web search query, retrieve relevant passages that answer the query.",
                                false,
                                null
                        ),
                        timeoutMs
                );
                boolean ok = res != null && res.results() != null && !res.results().isEmpty();
                out.setOk(ok);
                out.setUsedProviderId(res == null ? providerId : Objects.requireNonNullElse(res.providerId(), providerId));
                out.setUsedModel(res == null ? modelName : Objects.requireNonNullElse(res.model(), modelName));
                if (!ok) out.setErrorMessage("rerank 响应为空");
            } else if ("CHAT".equals(kind)) {
                LlmQueueTaskType tt = LlmQueueTaskType.MULTIMODAL_CHAT;
                List<ChatMessage> messages = buildProbeMessages();
                LlmGateway.RoutedChatOnceResult routed = withTimeout(
                        () -> llmGateway.chatOnceRouted(tt, providerId, modelName, messages, 0.0, 8, List.of("\n")),
                        timeoutMs
                );
                String text = routed == null ? null : routed.text();
                boolean ok = text != null && !text.trim().isEmpty();
                out.setOk(ok);
                out.setUsedProviderId(routed == null ? providerId : Objects.requireNonNullElse(routed.providerId(), providerId));
                out.setUsedModel(routed == null ? modelName : Objects.requireNonNullElse(routed.model(), modelName));
                if (!ok) out.setErrorMessage("chat 响应为空");
            } else {
                throw new IllegalArgumentException("不支持的 kind: " + kind);
            }
        } catch (TimeoutException te) {
            out.setOk(false);
            out.setErrorMessage("timeout");
        } catch (Exception e) {
            out.setOk(false);
            out.setErrorMessage(safeMessage(e));
        } finally {
            out.setLatencyMs(Math.max(0L, System.currentTimeMillis() - startedAt));
        }
        return out;
    }

    private static List<ChatMessage> buildProbeMessages() {
        String sys = "探活：只输出 ok。";
        return List.of(
                ChatMessage.system(sys),
                ChatMessage.user("ok")
        );
    }

    private <T> T withTimeout(Callable<T> task, long timeoutMs) throws Exception {
        Future<T> fut = executor.submit(task);
        try {
            return fut.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            fut.cancel(true);
            throw te;
        }
    }

    private static String safeMessage(Throwable e) {
        if (e == null) return "error";
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) return msg;
        return e.getClass().getSimpleName();
    }

    private static String normalizeKind(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
