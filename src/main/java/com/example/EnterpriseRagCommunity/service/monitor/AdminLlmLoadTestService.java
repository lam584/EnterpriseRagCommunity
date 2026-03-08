package com.example.EnterpriseRagCommunity.service.monitor;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestQueuePeakDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestResultDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestStatusDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunDetailEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmPriceConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminLlmLoadTestService {

    private static final int MAX_RUNS = 20;
    private static final int MAX_RESULTS = 5000;
    private static final int MAX_DETAIL_CHARS = 20_000;
    private static final String TRUNC_SUFFIX = "\n...(truncated)...";
    private static final String ENV_DEFAULT = "default";

    private final LlmGateway llmGateway;
    private final AdminModerationLlmService adminModerationLlmService;
    private final LlmQueueMonitorService llmQueueMonitorService;
    private final TokenCountService tokenCountService;
    private final ObjectMapper objectMapper;
    private final LlmModelRepository llmModelRepository;
    private final LlmPriceConfigRepository llmPriceConfigRepository;
    private final LlmLoadTestRunDetailRepository llmLoadTestRunDetailRepository;
    private final LlmLoadTestRunHistoryRepository llmLoadTestRunHistoryRepository;
    private final PromptsRepository promptsRepository;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService tokenExecutor = Executors.newFixedThreadPool(8);
    private final ConcurrentHashMap<String, RunState> runs = new ConcurrentHashMap<>();
    private final ArrayDeque<String> runOrder = new ArrayDeque<>();
    private final Object runOrderLock = new Object();

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        tokenExecutor.shutdownNow();
    }

    public String start(AdminLlmLoadTestRunRequestDTO req) {
        NormalizedConfig cfg = normalize(req);
        String runId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        RunState st = new RunState(runId, now, cfg);
        runs.put(runId, st);

        synchronized (runOrderLock) {
            runOrder.addLast(runId);
            while (runOrder.size() > MAX_RUNS) {
                String oldest = runOrder.removeFirst();
                RunState removed = runs.remove(oldest);
                if (removed != null) removed.cancel();
            }
        }

        st.startFut = executor.submit(() -> runInternal(st));
        return runId;
    }

    public boolean stop(String runId) {
        RunState st = runs.get(runId);
        if (st == null) return false;
        st.cancel();
        return true;
    }

    public AdminLlmLoadTestStatusDTO status(String runId) {
        RunState st = runs.get(runId);
        if (st == null) return null;
        return st.toStatus();
    }

    public ResponseEntity<StreamingResponseBody> export(String runId, String format) {
        String f = (format == null ? "json" : format.trim().toLowerCase(Locale.ROOT));
        boolean csv = "csv".equals(f);

        boolean hasAny = llmLoadTestRunDetailRepository.existsByRunId(runId);
        if (!hasAny && !llmLoadTestRunHistoryRepository.existsById(runId)) {
            return ResponseEntity.notFound().build();
        }

        String filename = csv ? ("llm-loadtest-" + runId + ".csv") : ("llm-loadtest-" + runId + ".json");
        MediaType mt = csv ? MediaType.valueOf("text/csv; charset=UTF-8") : MediaType.APPLICATION_JSON;

        StreamingResponseBody body = outputStream -> {
            if (csv) {
                writeCsvExport(runId, outputStream);
            } else {
                writeJsonExport(runId, outputStream);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mt)
                .body(body);
    }

    private void runInternal(RunState st) {
        st.running.set(true);
        st.startedAtMs.set(System.currentTimeMillis());
        st.queueMonitorFut = executor.submit(() -> monitorQueue(st));
        st.persistFut = executor.submit(() -> persistLoop(st));

        List<Future<?>> workers = new ArrayList<>();
        for (int i = 0; i < st.cfg.concurrency; i++) {
            workers.add(executor.submit(() -> workerLoop(st)));
        }
        st.workerFuts = workers;

        for (Future<?> f : workers) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }

        st.computeLatencyStats();
        st.persistClosed.set(true);
        try {
            if (st.persistFut != null) st.persistFut.get(30, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        st.running.set(false);
        st.finishedAtMs.set(System.currentTimeMillis());
        try {
            if (st.queueMonitorFut != null) st.queueMonitorFut.get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private void monitorQueue(RunState st) {
        while (!st.cancelled.get() && st.running.get()) {
            try {
                sampleQueue(st);
            } catch (Exception ignored) {
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (st.cancelled.get()) return;
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            sampleQueue(st);
        } catch (Exception ignored) {
        }
    }

    private void sampleQueue(RunState st) {
        var s = llmQueueMonitorService.query(60, 0, 0, 1);
        int pending = s == null || s.getPendingCount() == null ? 0 : Math.max(0, s.getPendingCount());
        int runningCount = s == null || s.getRunningCount() == null ? 0 : Math.max(0, s.getRunningCount());
        int total = pending + runningCount;
        st.queueMaxPending.accumulateAndGet(pending, Math::max);
        st.queueMaxRunning.accumulateAndGet(runningCount, Math::max);
        st.queueMaxTotal.accumulateAndGet(total, Math::max);

        double tps = 0.0;
        if (s != null && s.getSamples() != null && !s.getSamples().isEmpty()) {
            var last = s.getSamples().get(s.getSamples().size() - 1);
            if (last != null && last.getTokensPerSec() != null) tps = Math.max(0.0, last.getTokensPerSec());
        }
        if (tps > 0) {
            st.queueTokensPerSecMax.accumulate(tps);
            st.queueTokensPerSecSum.add(tps);
            st.queueTokensPerSecCount.increment();
        }
    }

    private void workerLoop(RunState st) {
        while (!st.cancelled.get()) {
            int idx = st.nextIndex.getAndIncrement();
            if (idx >= st.cfg.totalRequests) return;

            RequestKind kind = weightedPick(st.cfg, st.rnd.getAndIncrement());
            PreparedRequest prepared = prepareRequest(st, kind, idx);
            long startedAt = System.currentTimeMillis();

            try {
                Result result = withRetry(st, prepared);
                long finishedAt = System.currentTimeMillis();

                AdminLlmLoadTestResultDTO dto = new AdminLlmLoadTestResultDTO();
                dto.setIndex(idx);
                dto.setKind(kind.name());
                dto.setOk(true);
                dto.setLatencyMs(result.latencyMs != null ? result.latencyMs : (finishedAt - startedAt));
                dto.setStartedAtMs(startedAt);
                dto.setFinishedAtMs(finishedAt);
                dto.setTokens(result.tokens);
                dto.setTokensIn(result.tokensIn);
                dto.setTokensOut(result.tokensOut);
                dto.setModel(result.model);
                st.pushResult(dto);
                st.pushDetail(buildDetailEntity(st, dto, result.providerId, prepared.requestJson, result.responseJson));
                st.success.incrementAndGet();
                if (result.tokens != null) st.totalTokens.add(result.tokens);
                if (result.tokensIn != null) st.totalTokensIn.add(result.tokensIn);
                if (result.tokensOut != null) st.totalTokensOut.add(result.tokensOut);
                String modelKey = normalizeModel(result.model);
                if (modelKey == null) modelKey = normalizeModel(st.cfg.model);
                if (modelKey != null) {
                    ModelAgg agg = st.tokensByModel.computeIfAbsent(modelKey, k -> new ModelAgg());
                    if (result.tokensIn != null) agg.in.add(result.tokensIn);
                    if (result.tokensOut != null) agg.out.add(result.tokensOut);
                }
                maybeRecomputeTokensAsync(st, dto, result);
                long okLatency = dto.getLatencyMs() == null ? (finishedAt - startedAt) : dto.getLatencyMs();
                st.pushOkLatency(okLatency);
            } catch (Exception e) {
                long finishedAt = System.currentTimeMillis();
                AdminLlmLoadTestResultDTO dto = new AdminLlmLoadTestResultDTO();
                dto.setIndex(idx);
                dto.setKind(kind.name());
                dto.setOk(false);
                dto.setLatencyMs(finishedAt - startedAt);
                dto.setStartedAtMs(startedAt);
                dto.setFinishedAtMs(finishedAt);
                dto.setError(e instanceof TimeoutException ? "timeout" : safeMessage(e));
                st.pushResult(dto);
                st.pushDetail(buildDetailEntity(st, dto, st.cfg.providerId, prepared == null ? null : prepared.requestJson, null));
                st.failed.incrementAndGet();
            } finally {
                st.done.incrementAndGet();
            }
        }
    }

    private Result withRetry(RunState st, PreparedRequest prepared) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= st.cfg.retries; attempt++) {
            if (st.cancelled.get()) throw new InterruptedException("cancelled");
            try {
                return callOnce(st, prepared);
            } catch (Exception e) {
                last = e;
                if (attempt >= st.cfg.retries) break;
                if (st.cfg.retryDelayMs > 0) {
                    try {
                        Thread.sleep(st.cfg.retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedException("cancelled");
                    }
                }
            }
        }
        throw last == null ? new IllegalStateException("failed") : last;
    }

    private Result callOnce(RunState st, PreparedRequest prepared) throws Exception {
        if (prepared.kind == RequestKind.MODERATION_TEST) {
            return callModeration(st, prepared);
        }
        return callChatStream(st, prepared);
    }

    private Result callModeration(RunState st, PreparedRequest prepared) throws Exception {
        long startedAt = System.currentTimeMillis();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText(prepared.moderationText);

        LlmModerationTestResponse resp = runWithTimeout(() -> adminModerationLlmService.test(req), st.cfg.timeoutMs);
        Long latencyMs = resp == null || resp.getLatencyMs() == null ? null : resp.getLatencyMs().longValue();
        Integer tokens = null;
        Integer tokensIn = null;
        Integer tokensOut = null;
        if (resp != null && resp.getUsage() != null) {
            tokens = resp.getUsage().getTotalTokens();
            tokensIn = resp.getUsage().getPromptTokens();
            tokensOut = resp.getUsage().getCompletionTokens();
        }
        String model = resp == null ? null : resp.getModel();
        if (model == null || model.isBlank()) model = st.cfg.model;
        if (latencyMs == null) latencyMs = System.currentTimeMillis() - startedAt;
        List<ChatMessage> promptMessages = null;
        String rawModelOutput = null;
        String tokenCountTextRaw = null;
        String tokenCountText = null;
        String tokenCountTextForCount = null;
        boolean strippedThink = false;
        boolean strippedWhitespace = false;
        if (resp != null && resp.getPromptMessages() != null && !resp.getPromptMessages().isEmpty()) {
            List<ChatMessage> list = new ArrayList<>();
            for (var m : resp.getPromptMessages()) {
                if (m == null) continue;
                String role = m.getRole();
                if (role == null || role.isBlank()) continue;
                list.add(new ChatMessage(role, Objects.requireNonNullElse(m.getContent(), "")));
            }
            if (!list.isEmpty()) promptMessages = list;
            rawModelOutput = resp.getRawModelOutput();
            String extractedText = extractAssistantContentFromRawModelOutput(rawModelOutput);
            TokenCountService.NormalizedOutput norm = tokenCountService.normalizeOutputText(extractedText, st.cfg.enableThinking);
            tokenCountTextRaw = norm.rawText();
            tokenCountText = norm.displayText();
            tokenCountTextForCount = norm.tokenText();
            strippedThink = norm.strippedThink();
            strippedWhitespace = norm.strippedWhitespace();
            if (!st.cfg.enableThinking && (strippedThink || strippedWhitespace)) {
                tokensOut = tokenCountService.countTextTokens(norm.tokenText());
                if (tokensIn == null && promptMessages != null && !promptMessages.isEmpty()) {
                    tokensIn = tokenCountService.countChatMessagesTokens(promptMessages);
                }
                if (tokensIn != null && tokensOut != null) {
                    tokens = tokensIn + tokensOut;
                } else {
                    tokens = null;
                }
            }
        }
        Map<String, Object> respJson = new HashMap<>();
        respJson.put("kind", RequestKind.MODERATION_TEST.name());
        respJson.put("model", model);
        respJson.put("assistantTextRaw", tokenCountTextRaw);
        respJson.put("assistantText", tokenCountText);
        respJson.put("rawModelOutput", rawModelOutput);
        respJson.put("usage", resp == null ? null : resp.getUsage());
        respJson.put("strippedThink", strippedThink);
        respJson.put("strippedWhitespace", strippedWhitespace);
        respJson.put("decision", resp == null ? null : resp.getDecision());
        respJson.put("score", resp == null ? null : resp.getScore());
        respJson.put("reasons", resp == null ? null : resp.getReasons());
        respJson.put("riskTags", resp == null ? null : resp.getRiskTags());
        String responseJson = toJson(respJson);
        return new Result(latencyMs, tokens, tokensIn, tokensOut, model, promptMessages, tokenCountTextForCount, responseJson, st.cfg.providerId);
    }

    private Result callChatStream(RunState st, PreparedRequest prepared) throws Exception {
        long startedAt = System.currentTimeMillis();
        List<ChatMessage> messages = prepared.chatMessages;

        StringBuilder assistantAccum = new StringBuilder();
        LlmGateway.RoutedChatStreamResult routed = runWithTimeout(
                () -> llmGateway.chatStreamRouted(
                        LlmQueueTaskType.TEXT_CHAT,
                        st.cfg.providerId,
                        st.cfg.model,
                        messages,
                        null,
                        st.cfg.enableThinking,
                        null,
                        line -> appendStreamDelta(line, assistantAccum, st.cfg.enableThinking)
                ),
                st.cfg.timeoutMs
        );
        long latencyMs = System.currentTimeMillis() - startedAt;
        String model = routed == null ? null : routed.model();
        if (model == null || model.isBlank()) model = st.cfg.model;
        String tokenCountTextRaw = assistantAccum.toString();
        String providerId = routed == null ? st.cfg.providerId : routed.providerId();
        TokenCountService.TokenDecision dec = tokenCountService.decideChatTokens(
                providerId,
                model,
                st.cfg.enableThinking,
                routed == null ? null : routed.usage(),
                messages,
                tokenCountTextRaw
        );
        Integer tokens = dec == null ? null : dec.totalTokens();
        Integer tokensIn = dec == null ? null : dec.tokensIn();
        Integer tokensOut = dec == null ? null : dec.tokensOut();
        TokenCountService.NormalizedOutput norm = dec == null ? null : dec.normalizedOutput();
        String tokenCountText = norm == null ? tokenCountTextRaw : norm.displayText();
        String tokenCountTextForCount = norm == null ? tokenCountTextRaw : norm.tokenText();
        Map<String, Object> respJson = new HashMap<>();
        respJson.put("kind", RequestKind.CHAT_STREAM.name());
        respJson.put("providerId", providerId);
        respJson.put("model", model);
        respJson.put("assistantTextRaw", tokenCountTextRaw);
        respJson.put("assistantText", tokenCountText);
        respJson.put("usage", routed == null ? null : routed.usage());
        respJson.put("strippedThink", norm != null && norm.strippedThink());
        respJson.put("strippedWhitespace", norm != null && norm.strippedWhitespace());
        respJson.put("tokensOutSource", dec == null ? null : dec.tokensOutSource());
        String responseJson = toJson(respJson);
        return new Result(latencyMs, tokens, tokensIn, tokensOut, model, messages, tokenCountTextForCount, responseJson, providerId);
    }

    private void maybeRecomputeTokensAsync(RunState st, AdminLlmLoadTestResultDTO dto, Result result) {
        if (st == null || dto == null || result == null) return;
        if (st.cancelled.get()) return;
        List<ChatMessage> messages = result.tokenCountMessages;
        if (messages == null || messages.isEmpty()) return;
        tokenExecutor.submit(() -> {
            if (st.cancelled.get()) return;
            Integer estIn = null;
            boolean forceRecomputeIn = RequestKind.MODERATION_TEST.name().equals(dto.getKind());
            if (forceRecomputeIn || dto.getTokensIn() == null) {
                estIn = tokenCountService.countChatMessagesTokens(messages);
            }
            Integer estOut = null;
            if (dto.getTokensOut() == null) {
                estOut = tokenCountService.countTextTokens(result.tokenCountText);
            }

            Integer oldIn = dto.getTokensIn();
            Integer oldOut = dto.getTokensOut();
            Integer oldTotal = dto.getTokens();

            Integer newIn = forceRecomputeIn ? (estIn != null ? estIn : oldIn) : (oldIn != null ? oldIn : estIn);
            Integer newOut = estOut != null ? estOut : oldOut;
            Integer newTotal = null;
            if (newIn != null && newOut != null) {
                newTotal = newIn + newOut;
            } else if (oldTotal != null) {
                newTotal = oldTotal;
            }

            boolean changed = !Objects.equals(oldIn, newIn) || !Objects.equals(oldOut, newOut) || !Objects.equals(oldTotal, newTotal);
            if (!changed) return;

            synchronized (st.resultsLock) {
                dto.setTokensIn(newIn);
                dto.setTokensOut(newOut);
                dto.setTokens(newTotal);
            }

            if (newIn != null) {
                long delta = (oldIn == null) ? newIn.longValue() : (long) newIn - oldIn;
                if (delta != 0) st.totalTokensIn.add(delta);
            }
            if (newOut != null) {
                long delta = (oldOut == null) ? newOut.longValue() : (long) newOut - oldOut;
                if (delta != 0) st.totalTokensOut.add(delta);
            }
            if (newTotal != null) {
                long delta = (oldTotal == null) ? newTotal.longValue() : (long) newTotal - oldTotal;
                if (delta != 0) st.totalTokens.add(delta);
            }

            String modelKey = normalizeModel(dto.getModel());
            if (modelKey == null) modelKey = normalizeModel(st.cfg.model);
            if (modelKey != null) {
                ModelAgg agg = st.tokensByModel.computeIfAbsent(modelKey, k -> new ModelAgg());
                if (newIn != null) {
                    long delta = (oldIn == null) ? newIn.longValue() : (long) newIn - oldIn;
                    if (delta != 0) agg.in.add(delta);
                }
                if (newOut != null) {
                    long delta = (oldOut == null) ? newOut.longValue() : (long) newOut - oldOut;
                    if (delta != 0) agg.out.add(delta);
                }
            }
        });
    }

    private static void appendStreamDelta(String line, StringBuilder out, boolean includeReasoning) {
        if (out == null) return;
        if (line == null || line.isBlank()) return;
        if (!line.startsWith("data:")) return;
        String data = line.substring("data:".length()).trim();
        if ("[DONE]".equals(data)) return;
        String deltaText = extractDeltaText(data, includeReasoning);
        if (deltaText == null || deltaText.isEmpty()) return;
        out.append(deltaText);
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

    private static String stripReasoningArtifacts(String text) {
        if (text == null || text.isBlank()) return text;
        String t = removeMarkerWordIgnoreCase(text, "reasoning_content");
        t = removeMarkerWordIgnoreCase(t, "<reasoning_content>");
        t = removeMarkerWordIgnoreCase(t, "</reasoning_content>");
        t = removeMarkerWordIgnoreCase(t, "&lt;reasoning_content&gt;");
        t = removeMarkerWordIgnoreCase(t, "&lt;/reasoning_content&gt;");
        return t;
    }

    private String extractAssistantContentFromRawModelOutput(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (contentNode.isTextual()) return contentNode.asText();
                JsonNode textNode = first.path("text");
                if (textNode.isTextual()) return textNode.asText();
                JsonNode deltaNode = first.path("delta").path("content");
                if (deltaNode.isTextual()) return deltaNode.asText();
            }
            JsonNode outputText = root.path("output_text");
            if (outputText.isTextual()) return outputText.asText();
        } catch (Exception ignored) {
        }
        String content = extractDeltaStringField(raw, "content");
        return content != null ? content : raw;
    }

    private static String extractDeltaContent(String json) {
        return extractDeltaStringField(json, "content");
    }

    private static String extractDeltaText(String json, boolean includeReasoning) {
        if (json == null || json.isBlank()) return null;
        String reasoning = includeReasoning ? extractDeltaStringField(json, "reasoning_content") : null;
        String content = extractDeltaStringField(json, "content");
        String text = extractDeltaStringField(json, "text");
        StringBuilder sb = new StringBuilder();
        if (reasoning != null && !reasoning.isEmpty()) sb.append(reasoning);
        if (content != null && !content.isEmpty()) sb.append(content);
        if (sb.isEmpty() && text != null && !text.isEmpty()) sb.append(text);
        if (sb.isEmpty()) return null;
        String out = sb.toString();
        return includeReasoning ? out : stripReasoningArtifacts(out);
    }

    private static String extractDeltaStringField(String json, String field) {
        if (json == null) return null;
        String f = field == null ? "" : field.trim();
        if (f.isEmpty()) return null;
        int idx = json.indexOf("\"" + f + "\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int i = firstQuote + 1;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (esc) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (Exception ignore) {
                            }
                            i += 4;
                        }
                    }
                    default -> sb.append(c);
                }
                esc = false;
            } else {
                if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            i++;
        }
        return sb.toString();
    }

    private <T> T runWithTimeout(CheckedCallable<T> fn, int timeoutMs) throws Exception {
        int ms = Math.max(1, timeoutMs);
        Future<T> f = executor.submit(fn::call);
        try {
            return f.get(ms, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            f.cancel(true);
            throw te;
        } catch (Exception e) {
            f.cancel(true);
            if (e.getCause() instanceof Exception ex) throw ex;
            throw e;
        }
    }

    private static String safeMessage(Exception e) {
        if (e == null) return "error";
        String m = e.getMessage();
        if (m == null || m.isBlank()) return e.getClass().getSimpleName();
        return m;
    }

    private static RequestKind weightedPick(NormalizedConfig cfg, long counter) {
        int a = Math.max(0, cfg.weightChatStream);
        int b = Math.max(0, cfg.weightModeration);
        int sum = a + b;
        if (sum <= 0) return RequestKind.MODERATION_TEST;
        int u = (int) Math.floorMod(counter, sum);
        return u < a ? RequestKind.CHAT_STREAM : RequestKind.MODERATION_TEST;
    }

    private static NormalizedConfig normalize(AdminLlmLoadTestRunRequestDTO req) {
        int concurrency = clampInt(req == null ? null : req.getConcurrency(), 1, 500, 10);
        int totalRequests = clampInt(req == null ? null : req.getTotalRequests(), 1, 200_000, 100);
        int ratioChatStream = clampInt(req == null ? null : req.getRatioChatStream(), 0, 100, 70);
        int ratioModerationTest = clampInt(req == null ? null : req.getRatioModerationTest(), 0, 100, 30);
        boolean stream = req == null || req.getStream() == null || Boolean.TRUE.equals(req.getStream());
        boolean enableThinking = req != null && Boolean.TRUE.equals(req.getEnableThinking());
        String providerId = trimToNull(req == null ? null : req.getProviderId());
        String model = trimToNull(req == null ? null : req.getModel());
        int timeoutMs = clampInt(req == null ? null : req.getTimeoutMs(), 1000, 10 * 60_000, 60_000);
        int retries = clampInt(req == null ? null : req.getRetries(), 0, 10, 1);
        int retryDelayMs = clampInt(req == null ? null : req.getRetryDelayMs(), 0, 60_000, 200);
        String chatMessage = Objects.requireNonNullElse(trimToNull(req == null ? null : req.getChatMessage()), "压测：请用一句话回复“ok”。");
        String moderationText = Objects.requireNonNullElse(trimToNull(req == null ? null : req.getModerationText()), "压测：这是一条中性内容，用于审核模型吞吐测试。");

        int weightChatStream = stream ? ratioChatStream : 0;
        int weightModeration = ratioModerationTest;
        if (weightChatStream + weightModeration <= 0) {
            weightModeration = 100;
        }

        return new NormalizedConfig(
                concurrency,
                totalRequests,
                weightChatStream,
                weightModeration,
                providerId,
                model,
                enableThinking,
                timeoutMs,
                retries,
                retryDelayMs,
                chatMessage,
                moderationText
        );
    }

    private static int clampInt(Integer v, int min, int max, int def) {
        int x = v == null ? def : v;
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private String resolveModelNameForThinkDirective(String providerId, String modelOverride) {
        String m = toNonBlank(modelOverride);
        if (m != null) return m;
        String pid = toNonBlank(providerId);
        if (pid == null) return null;
        try {
            var p = llmGateway.resolve(pid);
            return toNonBlank(p == null ? null : p.defaultChatModel());
        } catch (Exception ignored) {
            return null;
        }
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

    private static String toNonBlank(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }

    private interface CheckedCallable<T> {
        T call() throws Exception;
    }

    private enum RequestKind {
        CHAT_STREAM,
        MODERATION_TEST
    }

    private record NormalizedConfig(
            int concurrency,
            int totalRequests,
            int weightChatStream,
            int weightModeration,
            String providerId,
            String model,
            boolean enableThinking,
            int timeoutMs,
            int retries,
            int retryDelayMs,
            String chatMessage,
            String moderationText
    ) {
    }

    private record Result(
            Long latencyMs,
            Integer tokens,
            Integer tokensIn,
            Integer tokensOut,
            String model,
            List<ChatMessage> tokenCountMessages,
            String tokenCountText,
            String responseJson,
            String providerId
    ) {}

    private record PreparedRequest(
            RequestKind kind,
            String requestJson,
            List<ChatMessage> chatMessages,
            String moderationText
    ) {}

    private final class RunState {
        private final String id;
        private final long createdAtMs;
        private final NormalizedConfig cfg;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicInteger done = new AtomicInteger(0);
        private final AtomicInteger success = new AtomicInteger(0);
        private final AtomicInteger failed = new AtomicInteger(0);
        private final LongAdder totalTokens = new LongAdder();
        private final LongAdder totalTokensIn = new LongAdder();
        private final LongAdder totalTokensOut = new LongAdder();
        private final ConcurrentHashMap<String, ModelAgg> tokensByModel = new ConcurrentHashMap<>();
        private final AtomicInteger nextIndex = new AtomicInteger(0);
        private final AtomicLong startedAtMs = new AtomicLong(0);
        private final AtomicLong finishedAtMs = new AtomicLong(0);
        private final AtomicLong rnd = new AtomicLong(Instant.now().toEpochMilli());

        private final AtomicInteger queueMaxPending = new AtomicInteger(0);
        private final AtomicInteger queueMaxRunning = new AtomicInteger(0);
        private final AtomicInteger queueMaxTotal = new AtomicInteger(0);
        private final DoubleAccumulator queueTokensPerSecMax = new DoubleAccumulator(Math::max, 0.0);
        private final DoubleAdder queueTokensPerSecSum = new DoubleAdder();
        private final LongAdder queueTokensPerSecCount = new LongAdder();

        private final BlockingQueue<LlmLoadTestRunDetailEntity> detailQueue;
        private final AtomicBoolean persistClosed = new AtomicBoolean(false);
        private volatile Future<?> persistFut;

        private final Object resultsLock = new Object();
        private final ArrayDeque<AdminLlmLoadTestResultDTO> results = new ArrayDeque<>();

        private final AtomicInteger okLatencyCount = new AtomicInteger(0);
        private final long[] okLatencies;

        private volatile Double avgLatencyMs;
        private volatile Long maxLatencyMs;
        private volatile Double p50LatencyMs;
        private volatile Double p95LatencyMs;
        private volatile String error;
        private volatile Future<?> startFut;
        private volatile Future<?> queueMonitorFut;
        private volatile List<Future<?>> workerFuts = List.of();

        private RunState(String id, long createdAtMs, NormalizedConfig cfg) {
            this.id = id;
            this.createdAtMs = createdAtMs;
            this.cfg = cfg;
            this.okLatencies = new long[Math.max(1, cfg.totalRequests)];
            int cap = Math.min(20_000, Math.max(1_000, cfg.concurrency * 200));
            this.detailQueue = new ArrayBlockingQueue<>(cap);
        }

        private void cancel() {
            cancelled.set(true);
            List<Future<?>> futs = workerFuts;
            if (futs != null) {
                for (Future<?> f : futs) {
                    if (f != null) f.cancel(true);
                }
            }
            if (queueMonitorFut != null) queueMonitorFut.cancel(true);
            if (startFut != null) startFut.cancel(true);
            persistClosed.set(true);
        }

        private void pushResult(AdminLlmLoadTestResultDTO dto) {
            if (dto == null) return;
            synchronized (resultsLock) {
                results.addLast(dto);
                while (results.size() > MAX_RESULTS) results.removeFirst();
            }
        }

        private void pushOkLatency(long latencyMs) {
            long v = Math.max(0L, latencyMs);
            int pos = okLatencyCount.getAndIncrement();
            if (pos >= 0 && pos < okLatencies.length) {
                okLatencies[pos] = v;
            }
        }

        private void pushDetail(LlmLoadTestRunDetailEntity e) {
            if (e == null) return;
            try {
                detailQueue.put(e);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        private void computeLatencyStats() {
            int n = Math.min(okLatencyCount.get(), okLatencies.length);
            if (n <= 0) {
                avgLatencyMs = null;
                maxLatencyMs = null;
                p50LatencyMs = null;
                p95LatencyMs = null;
                return;
            }
            long[] arr = Arrays.copyOf(okLatencies, n);
            Arrays.sort(arr);
            long max = arr[n - 1];
            double sum = 0.0;
            for (long x : arr) sum += x;
            avgLatencyMs = sum / n;
            maxLatencyMs = max;
            p50LatencyMs = percentile(arr, 0.5);
            p95LatencyMs = percentile(arr, 0.95);
        }

        private AdminLlmLoadTestStatusDTO toStatus() {
            AdminLlmLoadTestStatusDTO out = new AdminLlmLoadTestStatusDTO();
            out.setRunId(id);
            out.setCreatedAtMs(createdAtMs);
            long s = startedAtMs.get();
            out.setStartedAtMs(s <= 0 ? null : s);
            long f = finishedAtMs.get();
            out.setFinishedAtMs(f <= 0 ? null : f);
            out.setRunning(running.get());
            out.setCancelled(cancelled.get());
            out.setError(error);
            out.setDone(done.get());
            out.setTotal(cfg.totalRequests);
            out.setSuccess(success.get());
            out.setFailed(failed.get());
            out.setAvgLatencyMs(avgLatencyMs);
            out.setMaxLatencyMs(maxLatencyMs);
            out.setP50LatencyMs(p50LatencyMs);
            out.setP95LatencyMs(p95LatencyMs);
            out.setTokensTotal(totalTokens.sum());
            out.setTokensInTotal(totalTokensIn.sum());
            out.setTokensOutTotal(totalTokensOut.sum());
            if (!running.get()) {
                CostInfo cost = computeCostInfo(tokensByModel, cfg.enableThinking ? LlmPricing.Mode.THINKING : LlmPricing.Mode.NON_THINKING);
                out.setTotalCost(cost == null ? null : cost.totalCost());
                out.setCurrency(cost == null ? null : cost.currency());
                out.setPriceMissing(cost == null ? null : cost.priceMissing());
            }

            AdminLlmLoadTestQueuePeakDTO peak = new AdminLlmLoadTestQueuePeakDTO();
            peak.setMaxPending(queueMaxPending.get());
            peak.setMaxRunning(queueMaxRunning.get());
            peak.setMaxTotal(queueMaxTotal.get());
            peak.setTokensPerSecMax(queueTokensPerSecMax.get());
            long cnt = queueTokensPerSecCount.sum();
            double sum = queueTokensPerSecSum.sum();
            peak.setTokensPerSecAvg(cnt > 0 ? (sum / cnt) : 0.0);
            out.setQueuePeak(peak);

            List<AdminLlmLoadTestResultDTO> list = new ArrayList<>();
            synchronized (resultsLock) {
                list.addAll(results);
            }
            out.setRecentResults(list);
            return out;
        }
    }

    private PreparedRequest prepareRequest(RunState st, RequestKind kind, int idx) {
        if (kind == RequestKind.MODERATION_TEST) {
            String modelNameForDirective = resolveModelNameForThinkDirective(st.cfg.providerId, st.cfg.model);
            String text = st.cfg.moderationText + " #" + (idx + 1);
            text = applyThinkingDirective(text, st.cfg.enableThinking, modelNameForDirective);
            Map<String, Object> reqJson = new HashMap<>();
            reqJson.put("kind", kind.name());
            reqJson.put("text", text);
            reqJson.put("providerId", toNonBlank(st.cfg.providerId));
            reqJson.put("model", toNonBlank(st.cfg.model));
            reqJson.put("enableThinking", st.cfg.enableThinking);
            String requestJson = toJson(reqJson);
            return new PreparedRequest(kind, requestJson, null, text);
        }
        String modelNameForDirective = resolveModelNameForThinkDirective(st.cfg.providerId, st.cfg.model);
        String msg = st.cfg.chatMessage + " #" + (idx + 1);
        msg = applyThinkingDirective(msg, st.cfg.enableThinking, modelNameForDirective);
        String sysPrompt = promptsRepository.findByPromptCode("PORTAL_CHAT_ASSISTANT")
                .map(com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity::getSystemPrompt)
                .orElse("You are a helpful assistant.");
        List<ChatMessage> messages = List.of(
                ChatMessage.system(sysPrompt),
                ChatMessage.user(msg)
        );
        Map<String, Object> reqJson = new HashMap<>();
        reqJson.put("kind", kind.name());
        reqJson.put("messages", messages);
        reqJson.put("providerId", toNonBlank(st.cfg.providerId));
        reqJson.put("model", toNonBlank(st.cfg.model));
        reqJson.put("enableThinking", st.cfg.enableThinking);
        String requestJson = toJson(reqJson);
        return new PreparedRequest(kind, requestJson, messages, null);
    }

    private void persistLoop(RunState st) {
        List<LlmLoadTestRunDetailEntity> batch = new ArrayList<>(200);
        while (true) {
            try {
                LlmLoadTestRunDetailEntity first = st.detailQueue.poll(200, TimeUnit.MILLISECONDS);
                if (first != null) batch.add(first);
                st.detailQueue.drainTo(batch, Math.max(0, 200 - batch.size()));
                if (!batch.isEmpty()) {
                    llmLoadTestRunDetailRepository.saveAll(batch);
                    batch.clear();
                    continue;
                }
                if (st.persistClosed.get() && st.detailQueue.isEmpty()) break;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (st.persistClosed.get() && st.detailQueue.isEmpty()) break;
            } catch (Exception ignored) {
            }
        }
        if (!batch.isEmpty()) {
            try {
                llmLoadTestRunDetailRepository.saveAll(batch);
            } catch (Exception ignored) {
            }
        }
    }

    private LlmLoadTestRunDetailEntity buildDetailEntity(RunState st, AdminLlmLoadTestResultDTO dto, String providerId, String requestJson, String responseJson) {
        if (st == null || dto == null) return null;
        LlmLoadTestRunDetailEntity e = new LlmLoadTestRunDetailEntity();
        e.setRunId(st.id);
        e.setReqIndex(dto.getIndex());
        e.setKind(dto.getKind());
        e.setOk(Boolean.TRUE.equals(dto.getOk()));
        e.setStartedAt(toLocalDateTime(dto.getStartedAtMs()));
        e.setFinishedAt(toLocalDateTime(dto.getFinishedAtMs()));
        e.setLatencyMs(dto.getLatencyMs());
        String pid = toNonBlank(providerId);
        if (pid == null) pid = toNonBlank(st.cfg.providerId);
        e.setProviderId(pid);
        e.setModel(toNonBlank(dto.getModel()));
        e.setTokensIn(dto.getTokensIn());
        e.setTokensOut(dto.getTokensOut());
        e.setTotalTokens(dto.getTokens());
        e.setError(truncateError(dto.getError()));

        TruncatedText req = truncateText(requestJson);
        TruncatedText resp = truncateText(responseJson);
        e.setRequestJson(req.text);
        e.setResponseJson(resp.text);
        e.setRequestChars(req.text == null ? null : req.text.length());
        e.setResponseChars(resp.text == null ? null : resp.text.length());
        e.setRequestTruncated(req.truncated);
        e.setResponseTruncated(resp.truncated);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }

    private record TruncatedText(String text, boolean truncated) {}

    private TruncatedText truncateText(String s) {
        if (s == null) return new TruncatedText(null, false);
        String v = s;
        boolean truncated = false;
        if (v.length() > MAX_DETAIL_CHARS) {
            v = v.substring(0, MAX_DETAIL_CHARS) + TRUNC_SUFFIX;
            truncated = true;
        }
        return new TruncatedText(v, truncated);
    }

    private String truncateError(String s) {
        if (s == null) return null;
        String v = s.trim();
        if (v.isEmpty()) return null;
        if (v.length() <= 1024) return v;
        return v.substring(0, 1024);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj == null ? null : String.valueOf(obj);
        }
    }

    private LocalDateTime toLocalDateTime(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    private void writeJsonExport(String runId, OutputStream outputStream) throws java.io.IOException {
        String summaryJson = null;
        try {
            var hist = llmLoadTestRunHistoryRepository.findById(runId).orElse(null);
            if (hist != null) summaryJson = hist.getSummaryJson();
        } catch (Exception ignored) {
        }
        JsonNode summaryNode = tryReadJson(summaryJson);
        if (summaryNode == null) {
            RunState st = runs.get(runId);
            if (st != null) {
                Map<String, Object> cfg = new HashMap<>();
                cfg.put("concurrency", st.cfg.concurrency);
                cfg.put("totalRequests", st.cfg.totalRequests);
                cfg.put("providerId", toNonBlank(st.cfg.providerId));
                cfg.put("model", toNonBlank(st.cfg.model));
                cfg.put("stream", true);
                cfg.put("enableThinking", st.cfg.enableThinking);
                cfg.put("timeoutMs", st.cfg.timeoutMs);
                cfg.put("retries", st.cfg.retries);
                cfg.put("retryDelayMs", st.cfg.retryDelayMs);
                cfg.put("chatMessage", st.cfg.chatMessage);
                cfg.put("moderationText", st.cfg.moderationText);

                Map<String, Object> sum = new HashMap<>();
                sum.put("runId", runId);
                sum.put("createdAt", Instant.ofEpochMilli(st.createdAtMs).toString());
                long s = st.startedAtMs.get();
                long f = st.finishedAtMs.get();
                sum.put("startedAt", s > 0 ? Instant.ofEpochMilli(s).toString() : null);
                sum.put("finishedAt", f > 0 ? Instant.ofEpochMilli(f).toString() : null);
                sum.put("durationMs", (s > 0 && f > 0) ? Math.max(0, f - s) : null);
                sum.put("config", cfg);
                sum.put("success", st.success.get());
                sum.put("failed", st.failed.get());
                sum.put("successRate", Math.round((st.success.get() / Math.max(1.0, st.success.get() + st.failed.get())) * 10000.0) / 100.0);
                sum.put("tokensTotal", st.totalTokens.sum());
                sum.put("tokensInTotal", st.totalTokensIn.sum());
                sum.put("tokensOutTotal", st.totalTokensOut.sum());
                summaryNode = objectMapper.valueToTree(sum);
            }
        }

        try (JsonGenerator gen = objectMapper.getFactory().createGenerator(outputStream)) {
            gen.writeStartObject();
            gen.writeFieldName("summary");
            if (summaryNode != null) gen.writeTree(summaryNode);
            else gen.writeNull();

            gen.writeFieldName("details");
            gen.writeStartArray();

            int page = 0;
            int size = 2000;
            while (true) {
                Page<LlmLoadTestRunDetailEntity> p = llmLoadTestRunDetailRepository.findByRunIdOrderByReqIndexAsc(runId, PageRequest.of(page, size));
                for (LlmLoadTestRunDetailEntity e : p.getContent()) {
                    if (e == null) continue;
                    gen.writeStartObject();
                    gen.writeStringField("runId", e.getRunId());
                    gen.writeNumberField("index", e.getReqIndex() == null ? 0 : e.getReqIndex());
                    gen.writeStringField("kind", e.getKind());
                    gen.writeBooleanField("ok", Boolean.TRUE.equals(e.getOk()));
                    if (e.getStartedAt() == null) gen.writeNullField("startedAt");
                    else gen.writeStringField("startedAt", e.getStartedAt().toString());
                    if (e.getFinishedAt() == null) gen.writeNullField("finishedAt");
                    else gen.writeStringField("finishedAt", e.getFinishedAt().toString());
                    if (e.getLatencyMs() == null) gen.writeNullField("latencyMs");
                    else gen.writeNumberField("latencyMs", e.getLatencyMs());
                    if (e.getProviderId() == null) gen.writeNullField("providerId");
                    else gen.writeStringField("providerId", e.getProviderId());
                    if (e.getModel() == null) gen.writeNullField("model");
                    else gen.writeStringField("model", e.getModel());
                    if (e.getTokensIn() == null) gen.writeNullField("tokensIn");
                    else gen.writeNumberField("tokensIn", e.getTokensIn());
                    if (e.getTokensOut() == null) gen.writeNullField("tokensOut");
                    else gen.writeNumberField("tokensOut", e.getTokensOut());
                    if (e.getTotalTokens() == null) gen.writeNullField("tokens");
                    else gen.writeNumberField("tokens", e.getTotalTokens());
                    if (e.getError() == null) gen.writeNullField("error");
                    else gen.writeStringField("error", e.getError());
                    gen.writeBooleanField("requestTruncated", Boolean.TRUE.equals(e.getRequestTruncated()));
                    gen.writeBooleanField("responseTruncated", Boolean.TRUE.equals(e.getResponseTruncated()));
                    gen.writeFieldName("request");
                    writeMaybeJson(gen, e.getRequestJson());
                    gen.writeFieldName("response");
                    writeMaybeJson(gen, e.getResponseJson());
                    gen.writeEndObject();
                }
                gen.flush();
                if (!p.hasNext()) break;
                page++;
            }

            gen.writeEndArray();
            gen.writeEndObject();
            gen.flush();
        }
    }

    private void writeCsvExport(String runId, OutputStream outputStream) throws java.io.IOException {
        outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            w.write(String.join(",",
                    "runId",
                    "index",
                    "kind",
                    "ok",
                    "startedAt",
                    "finishedAt",
                    "latencyMs",
                    "providerId",
                    "model",
                    "tokensIn",
                    "tokensOut",
                    "tokens",
                    "error",
                    "requestTruncated",
                    "responseTruncated",
                    "requestJson",
                    "responseJson"
            ));
            w.write("\n");

            int page = 0;
            int size = 2000;
            while (true) {
                Page<LlmLoadTestRunDetailEntity> p = llmLoadTestRunDetailRepository.findByRunIdOrderByReqIndexAsc(runId, PageRequest.of(page, size));
                for (LlmLoadTestRunDetailEntity e : p.getContent()) {
                    if (e == null) continue;
                    w.write(csvEscape(e.getRunId()));
                    w.write(",");
                    w.write(csvEscape(e.getReqIndex() == null ? "" : String.valueOf(e.getReqIndex())));
                    w.write(",");
                    w.write(csvEscape(e.getKind()));
                    w.write(",");
                    w.write(csvEscape(Boolean.TRUE.equals(e.getOk()) ? "true" : "false"));
                    w.write(",");
                    w.write(csvEscape(e.getStartedAt() == null ? "" : e.getStartedAt().toString()));
                    w.write(",");
                    w.write(csvEscape(e.getFinishedAt() == null ? "" : e.getFinishedAt().toString()));
                    w.write(",");
                    w.write(csvEscape(e.getLatencyMs() == null ? "" : String.valueOf(e.getLatencyMs())));
                    w.write(",");
                    w.write(csvEscape(e.getProviderId()));
                    w.write(",");
                    w.write(csvEscape(e.getModel()));
                    w.write(",");
                    w.write(csvEscape(e.getTokensIn() == null ? "" : String.valueOf(e.getTokensIn())));
                    w.write(",");
                    w.write(csvEscape(e.getTokensOut() == null ? "" : String.valueOf(e.getTokensOut())));
                    w.write(",");
                    w.write(csvEscape(e.getTotalTokens() == null ? "" : String.valueOf(e.getTotalTokens())));
                    w.write(",");
                    w.write(csvEscape(e.getError()));
                    w.write(",");
                    w.write(csvEscape(Boolean.TRUE.equals(e.getRequestTruncated()) ? "true" : "false"));
                    w.write(",");
                    w.write(csvEscape(Boolean.TRUE.equals(e.getResponseTruncated()) ? "true" : "false"));
                    w.write(",");
                    w.write(csvEscape(e.getRequestJson()));
                    w.write(",");
                    w.write(csvEscape(e.getResponseJson()));
                    w.write("\n");
                }
                w.flush();
                if (!p.hasNext()) break;
                page++;
            }
        }
    }

    private JsonNode tryReadJson(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return objectMapper.readTree(t);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeMaybeJson(JsonGenerator gen, String jsonText) throws java.io.IOException {
        if (jsonText == null) {
            gen.writeNull();
            return;
        }
        JsonNode node = tryReadJson(jsonText);
        if (node != null) {
            gen.writeTree(node);
        } else {
            gen.writeString(jsonText);
        }
    }

    private static String csvEscape(String v) {
        if (v == null) return "";
        String s = v;
        boolean needQuote = s.contains("\"") || s.contains(",") || s.contains("\n") || s.contains("\r");
        if (!needQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static final class ModelAgg {
        private final LongAdder in = new LongAdder();
        private final LongAdder out = new LongAdder();
    }

    private record PriceInfo(String currency, LlmPricing.Config pricing) {
    }

    private record CostInfo(BigDecimal totalCost, String currency, boolean priceMissing) {
    }

    private CostInfo computeCostInfo(Map<String, ModelAgg> aggByModel, LlmPricing.Mode pricingMode) {
        if (aggByModel == null || aggByModel.isEmpty()) return null;
        Map<String, ModelAgg> byModel = aggByModel;

        Set<String> models = new HashSet<>();
        for (String m : byModel.keySet()) {
            String nm = normalizeModel(m);
            if (nm != null) models.add(nm);
        }
        if (models.isEmpty()) return null;

        Map<String, PriceInfo> priceByModel = resolvePrices(models);
        BigDecimal totalCost = BigDecimal.ZERO;
        Set<String> currencies = new HashSet<>();
        boolean priceMissing = false;

        for (Map.Entry<String, ModelAgg> en : byModel.entrySet()) {
            String model = normalizeModel(en.getKey());
            if (model == null) continue;
            ModelAgg agg = en.getValue();
            long in = agg == null ? 0L : agg.in.sum();
            long out = agg == null ? 0L : agg.out.sum();
            PriceInfo price = priceByModel.get(model);
            if (price != null && price.currency() != null) currencies.add(price.currency());
            if (price == null || !LlmPricing.isConfiguredForMode(price.pricing(), pricingMode)) priceMissing = true;
            totalCost = totalCost.add(TokenCostCalculator.computeCost(price == null ? null : price.pricing(), pricingMode, in, out));
        }

        String currency = null;
        if (currencies.size() == 1) currency = currencies.iterator().next();
        if (currencies.size() > 1) currency = "MIXED";
        return new CostInfo(totalCost, currency, priceMissing);
    }

    private Map<String, PriceInfo> resolvePrices(Collection<String> models) {
        List<String> ms = new ArrayList<>();
        if (models != null) {
            for (String m : models) {
                String nm = normalizeModel(m);
                if (nm != null) ms.add(nm);
            }
        }
        if (ms.isEmpty()) return Map.of();

        Map<String, Long> priceIdByModel = new HashMap<>();
        List<LlmModelEntity> modelEntities = llmModelRepository.findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(ENV_DEFAULT, ms);
        if (modelEntities != null) {
            for (LlmModelEntity me : modelEntities) {
                if (me == null) continue;
                String model = normalizeModel(me.getModelName());
                if (model == null) continue;
                Long pid = me.getPriceConfigId();
                if (pid != null && pid > 0) priceIdByModel.put(model, pid);
            }
        }

        Set<Long> ids = new HashSet<>(priceIdByModel.values());
        Map<Long, LlmPriceConfigEntity> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            List<LlmPriceConfigEntity> pcs = llmPriceConfigRepository.findByIdIn(ids);
            if (pcs != null) {
                for (LlmPriceConfigEntity pc : pcs) {
                    if (pc == null || pc.getId() == null) continue;
                    byId.put(pc.getId(), pc);
                }
            }
        }

        Map<String, PriceInfo> out = new HashMap<>();
        Set<String> missingModels = new HashSet<>();
        for (String model : ms) {
            Long pid = priceIdByModel.get(model);
            LlmPriceConfigEntity pc = pid == null ? null : byId.get(pid);
            if (pc == null) {
                missingModels.add(model);
                continue;
            }
            out.put(model, new PriceInfo(normalizeCurrency(pc.getCurrency()), resolvePricing(pc)));
        }

        if (!missingModels.isEmpty()) {
            List<LlmPriceConfigEntity> pcs = llmPriceConfigRepository.findByNameIn(missingModels);
            if (pcs != null) {
                for (LlmPriceConfigEntity pc : pcs) {
                    if (pc == null) continue;
                    String name = normalizeModel(pc.getName());
                    if (name == null) continue;
                    if (!missingModels.contains(name)) continue;
                    out.putIfAbsent(name, new PriceInfo(normalizeCurrency(pc.getCurrency()), resolvePricing(pc)));
                }
            }
        }

        return out;
    }

    private static LlmPricing.Config resolvePricing(LlmPriceConfigEntity pc) {
        if (pc == null) return null;
        LlmPricing.Config meta = LlmPricing.fromMetadata(pc.getMetadata());
        if (meta != null) return meta;
        return LlmPricing.fromLegacy(pc.getInputCostPer1k(), pc.getOutputCostPer1k());
    }

    private static String normalizeModel(String model) {
        if (model == null) return null;
        String s = model.trim();
        if (s.isBlank()) return null;
        String base = s;
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < base.length()) base = base.substring(slash + 1);
        int colon = base.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < base.length()) base = base.substring(colon + 1);
        String out = base.trim();
        return out.isBlank() ? s : out;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String s = currency.trim();
        return s.isBlank() ? null : s.toUpperCase(Locale.ROOT);
    }

    private static Double percentile(long[] sortedAsc, double p) {
        if (sortedAsc == null || sortedAsc.length == 0) return null;
        if (!Double.isFinite(p)) return null;
        double q = Math.max(0.0, Math.min(1.0, p));
        if (sortedAsc.length == 1) return (double) sortedAsc[0];
        double idx = (sortedAsc.length - 1) * q;
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        long a = sortedAsc[Math.min(sortedAsc.length - 1, Math.max(0, lo))];
        long b = sortedAsc[Math.min(sortedAsc.length - 1, Math.max(0, hi))];
        if (hi == lo) return (double) a;
        double t = idx - lo;
        return a + (b - a) * t;
    }
}
