package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class LlmCallQueueService {

    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public interface CheckedTaskSupplier<T> {
        T get(TaskHandle task) throws Exception;
    }

    public interface ResultMetricsExtractor<T> {
        UsageMetrics extract(T result) throws Exception;
    }

    public record UsageMetrics(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer estimatedCompletionTokens
    ) {
        public Integer tokensOut() {
            if (completionTokens != null && completionTokens > 0) return completionTokens;
            if (estimatedCompletionTokens != null && estimatedCompletionTokens > 0) return estimatedCompletionTokens;
            return null;
        }
    }

    @Getter
    public static class TaskSnapshot {
        private final String id;
        private final long seq;
        private final int priority;
        private final LlmQueueTaskType type;
        private final LlmQueueTaskStatus status;
        private final String providerId;
        private final String model;
        private final long createdAtMs;
        private final Long startedAtMs;
        private final Long finishedAtMs;
        private final Long waitMs;
        private final Long durationMs;
        private final Integer tokensIn;
        private final Integer tokensOut;
        private final Integer totalTokens;
        private final Double tokensPerSec;
        private final String error;

        private TaskSnapshot(
                String id,
                long seq,
                int priority,
                LlmQueueTaskType type,
                LlmQueueTaskStatus status,
                String providerId,
                String model,
                long createdAtMs,
                Long startedAtMs,
                Long finishedAtMs,
                Long waitMs,
                Long durationMs,
                Integer tokensIn,
                Integer tokensOut,
                Integer totalTokens,
                Double tokensPerSec,
                String error
        ) {
            this.id = id;
            this.seq = seq;
            this.priority = priority;
            this.type = type;
            this.status = status;
            this.providerId = providerId;
            this.model = model;
            this.createdAtMs = createdAtMs;
            this.startedAtMs = startedAtMs;
            this.finishedAtMs = finishedAtMs;
            this.waitMs = waitMs;
            this.durationMs = durationMs;
            this.tokensIn = tokensIn;
            this.tokensOut = tokensOut;
            this.totalTokens = totalTokens;
            this.tokensPerSec = tokensPerSec;
            this.error = error;
        }

        public LocalDateTime createdAt() {
            return toLocalDateTime(createdAtMs);
        }

        public LocalDateTime startedAt() {
            return startedAtMs == null ? null : toLocalDateTime(startedAtMs);
        }

        public LocalDateTime finishedAt() {
            return finishedAtMs == null ? null : toLocalDateTime(finishedAtMs);
        }
    }

    public record QueueSnapshot(
            int maxConcurrent,
            int maxQueueSize,
            int runningCount,
            int pendingCount,
            List<TaskSnapshot> running,
            List<TaskSnapshot> pending,
            List<TaskSnapshot> recentCompleted
    ) {
    }

    public record TaskDetailSnapshot(
            String id,
            long seq,
            int priority,
            LlmQueueTaskType type,
            LlmQueueTaskStatus status,
            String providerId,
            String model,
            long createdAtMs,
            Long startedAtMs,
            Long finishedAtMs,
            Long waitMs,
            Long durationMs,
            Integer tokensIn,
            Integer tokensOut,
            Integer totalTokens,
            Double tokensPerSec,
            String error,
            String input,
            String output
    ) {
        public LocalDateTime createdAt() {
            return toLocalDateTime(createdAtMs);
        }

        public LocalDateTime startedAt() {
            return startedAtMs == null ? null : toLocalDateTime(startedAtMs);
        }

        public LocalDateTime finishedAt() {
            return finishedAtMs == null ? null : toLocalDateTime(finishedAtMs);
        }
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    private static final Comparator<Task> TASK_ORDER = (a, b) -> {
        int p = Integer.compare(b.priority, a.priority);
        if (p != 0) return p;
        return Long.compare(a.seq, b.seq);
    };

    private final LlmQueueProperties props;
    private final LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicLong seq = new AtomicLong(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final PriorityQueue<Task> pending = new PriorityQueue<>(TASK_ORDER);
    private final List<Task> running = new ArrayList<>();
    private final ArrayDeque<TaskSnapshot> completed = new ArrayDeque<>();
    private final HashMap<String, TaskDetailSnapshot> completedDetails = new HashMap<>();
    private final ArrayDeque<String> completedDetailOrder = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<TaskSnapshot>> completedListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private int active = 0;

    private static final int MAX_DETAIL_CHARS = 20000;

    public final class TaskHandle {
        private final String taskId;

        private TaskHandle(String taskId) {
            this.taskId = taskId;
        }

        public String id() {
            return taskId;
        }

        public void reportEstimatedTokensOut(int tokensOutSoFar) {
            updateRunningTokensOut(taskId, tokensOutSoFar);
        }

        public void reportInput(String input) {
            updateRunningInput(taskId, input);
        }

        public void reportOutput(String output) {
            updateRunningOutput(taskId, output);
        }
    }

    private static class Task {
        private final String id;
        private final long seq;
        private final int priority;
        private final LlmQueueTaskType type;
        private final String providerId;
        private final String model;
        private final long createdAtMs;
        private volatile LlmQueueTaskStatus status = LlmQueueTaskStatus.PENDING;
        private volatile Long startedAtMs;
        private volatile Long finishedAtMs;
        private volatile Integer tokensIn;
        private volatile Integer tokensOut;
        private volatile Integer totalTokens;
        private volatile Double tokensPerSec;
        private volatile String error;
        private volatile String input;
        private volatile String output;

        private Task(String id, long seq, int priority, LlmQueueTaskType type, String providerId, String model, long createdAtMs) {
            this.id = id;
            this.seq = seq;
            this.priority = priority;
            this.type = type == null ? LlmQueueTaskType.UNKNOWN : type;
            this.providerId = providerId;
            this.model = model;
            this.createdAtMs = createdAtMs;
        }

        private Long calcWaitMs() {
            Long waitMs = null;
            if (startedAtMs != null) {
                waitMs = Math.max(0L, startedAtMs - createdAtMs);
            }
            return waitMs;
        }

        private Long calcDurationMs() {
            Long durationMs = null;
            if (startedAtMs != null && finishedAtMs != null) {
                durationMs = Math.max(0L, finishedAtMs - startedAtMs);
            }
            return durationMs;
        }

        private TaskSnapshot toSnapshot() {
            Long waitMs = calcWaitMs();
            Long durationMs = calcDurationMs();
            return new TaskSnapshot(
                    id,
                    seq,
                    priority,
                    type,
                    status,
                    providerId,
                    model,
                    createdAtMs,
                    startedAtMs,
                    finishedAtMs,
                    waitMs,
                    durationMs,
                    tokensIn,
                    tokensOut,
                    totalTokens,
                    tokensPerSec,
                    error
            );
        }

        private TaskDetailSnapshot toDetailSnapshot() {
            Long waitMs = calcWaitMs();
            Long durationMs = calcDurationMs();
            return new TaskDetailSnapshot(
                    id,
                    seq,
                    priority,
                    type,
                    status,
                    providerId,
                    model,
                    createdAtMs,
                    startedAtMs,
                    finishedAtMs,
                    waitMs,
                    durationMs,
                    tokensIn,
                    tokensOut,
                    totalTokens,
                    tokensPerSec,
                    error,
                    input,
                    output
            );
        }
    }

    public <T> T call(
            LlmQueueTaskType type,
            String providerId,
            String model,
            int priority,
            CheckedSupplier<T> supplier,
            ResultMetricsExtractor<T> metricsExtractor
    ) throws Exception {
        return call(type, providerId, model, priority, (TaskHandle _t) -> supplier.get(), metricsExtractor);
    }

    public <T> T callDedup(
            LlmQueueTaskType type,
            String providerId,
            String model,
            int priority,
            String dedupKey,
            CheckedSupplier<T> supplier,
            ResultMetricsExtractor<T> metricsExtractor
    ) throws Exception {
        return callDedup(type, providerId, model, priority, dedupKey, (TaskHandle _t) -> supplier.get(), metricsExtractor);
    }

    public <T> T callDedup(
            LlmQueueTaskType type,
            String providerId,
            String model,
            int priority,
            String dedupKey,
            CheckedTaskSupplier<T> supplier,
            ResultMetricsExtractor<T> metricsExtractor
    ) throws Exception {
        if (dedupKey == null || dedupKey.isBlank()) {
            return call(type, providerId, model, priority, supplier, metricsExtractor);
        }

        String key = buildInFlightKey(type, providerId, model, dedupKey);
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                @SuppressWarnings("unchecked")
                T reused = (T) existing.get();
                return reused;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待去重任务结果被中断", ie);
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw new RuntimeException(cause);
            }
        }

        try {
            T result = call(type, providerId, model, priority, supplier, metricsExtractor);
            mine.complete(result);
            return result;
        } catch (Exception e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    public <T> T call(
            LlmQueueTaskType type,
            String providerId,
            String model,
            int priority,
            CheckedTaskSupplier<T> supplier,
            ResultMetricsExtractor<T> metricsExtractor
    ) throws Exception {
        Task task = enqueue(type, providerId, model, priority);
        TaskHandle handle = new TaskHandle(task.id);
        try {
            T result = supplier.get(handle);
            UsageMetrics metrics = null;
            if (metricsExtractor != null) {
                try {
                    metrics = metricsExtractor.extract(result);
                } catch (Exception ignore) {
                    metrics = null;
                }
            }
            finishSuccess(task, metrics);
            return result;
        } catch (Exception e) {
            finishFailure(task, e);
            throw e;
        }
    }

    public <T> T call(
            LlmQueueTaskType type,
            String providerId,
            String model,
            CheckedSupplier<T> supplier,
            ResultMetricsExtractor<T> metricsExtractor
    ) throws Exception {
        return call(type, providerId, model, 0, supplier, metricsExtractor);
    }

    private static String buildInFlightKey(LlmQueueTaskType type, String providerId, String model, String dedupKey) {
        String t = type == null ? "UNKNOWN" : type.name();
        String p = providerId == null ? "" : providerId.trim();
        String m = model == null ? "" : model.trim();
        String d = dedupKey == null ? "" : dedupKey.trim();
        return t + "|" + p + "|" + m + "|" + d;
    }

    private void updateRunningTokensOut(String taskId, int tokensOutSoFar) {
        if (taskId == null || taskId.isBlank()) return;
        int next = Math.max(0, tokensOutSoFar);
        long now = System.currentTimeMillis();
        lock.lock();
        try {
            for (Task t : running) {
                if (t == null) continue;
                if (!taskId.equals(t.id)) continue;
                Integer prev = t.tokensOut;
                if (prev != null && prev >= next) return;
                t.tokensOut = next;
                if (t.tokensIn != null) t.totalTokens = t.tokensIn + next;
                else t.totalTokens = next;
                if (t.startedAtMs != null && next > 0) {
                    long durMs = Math.max(1L, now - t.startedAtMs);
                    t.tokensPerSec = (next * 1000.0) / durMs;
                }
                return;
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateRunningInput(String taskId, String input) {
        if (taskId == null || taskId.isBlank()) return;
        String next = clampDetail(input);
        lock.lock();
        try {
            for (Task t : running) {
                if (t == null) continue;
                if (!taskId.equals(t.id)) continue;
                t.input = next;
                return;
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateRunningOutput(String taskId, String output) {
        if (taskId == null || taskId.isBlank()) return;
        String next = clampDetail(output);
        lock.lock();
        try {
            for (Task t : running) {
                if (t == null) continue;
                if (!taskId.equals(t.id)) continue;
                t.output = next;
                return;
            }
        } finally {
            lock.unlock();
        }
    }

    private static String clampDetail(String s) {
        if (s == null) return null;
        String t = s;
        if (t.length() <= MAX_DETAIL_CHARS) return t;
        return t.substring(0, MAX_DETAIL_CHARS) + "\n...(truncated)...";
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

    private static UsageMetrics normalizeOpenAiCompatUsage(Integer prompt, Integer completion, Integer total) {
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
        return new UsageMetrics(p, c, t, null);
    }

    public UsageMetrics parseOpenAiUsageFromJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) return null;
            JsonNode usage = root.path("usage");
            JsonNode usageNode = usage != null && usage.isObject() ? usage : root;
            Integer prompt = pickIntLoose(usageNode, "prompt_tokens", "input_tokens", "promptTokens", "inputTokens");
            Integer completion = pickIntLoose(usageNode, "completion_tokens", "output_tokens", "completionTokens", "outputTokens");
            Integer total = pickIntLoose(usageNode, "total_tokens", "totalTokens");
            return normalizeOpenAiCompatUsage(prompt, completion, total);
        } catch (Exception e) {
            return null;
        }
    }

    public QueueSnapshot snapshot() {
        return snapshot(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public QueueSnapshot snapshot(int maxRunning, int maxPending, int maxCompleted) {
        int runLimit = Math.max(0, maxRunning);
        int pendLimit = Math.max(0, maxPending);
        int doneLimit = Math.max(0, maxCompleted);

        lock.lock();
        try {
            List<TaskSnapshot> runningSnaps;
            if (runLimit <= 0 || running.isEmpty()) {
                runningSnaps = List.of();
            } else {
                runningSnaps = new ArrayList<>(Math.min(running.size(), runLimit));
                for (Task t : running) runningSnaps.add(t.toSnapshot());
                runningSnaps.sort(Comparator.comparingLong(TaskSnapshot::getSeq));
                if (runningSnaps.size() > runLimit) runningSnaps = runningSnaps.subList(0, runLimit);
            }

            List<TaskSnapshot> pendingSnaps;
            if (pendLimit <= 0 || pending.isEmpty()) {
                pendingSnaps = List.of();
            } else {
                int take = Math.min(pending.size(), pendLimit);
                List<Task> drained = new ArrayList<>(take);
                pendingSnaps = new ArrayList<>(take);
                for (int i = 0; i < take; i++) {
                    Task t = pending.poll();
                    if (t == null) break;
                    drained.add(t);
                    pendingSnaps.add(t.toSnapshot());
                }
                for (Task t : drained) pending.add(t);
            }

            List<TaskSnapshot> completedSnaps;
            if (doneLimit <= 0 || completed.isEmpty()) {
                completedSnaps = List.of();
            } else if (completed.size() <= doneLimit) {
                completedSnaps = new ArrayList<>(completed);
            } else {
                completedSnaps = new ArrayList<>(doneLimit);
                for (TaskSnapshot t : completed) {
                    if (t == null) continue;
                    completedSnaps.add(t);
                    if (completedSnaps.size() >= doneLimit) break;
                }
            }
            return new QueueSnapshot(
                    Math.max(1, props.getMaxConcurrent()),
                    Math.max(1, props.getMaxQueueSize()),
                    active,
                    pending.size(),
                    runningSnaps,
                    pendingSnaps,
                    completedSnaps
            );
        } finally {
            lock.unlock();
        }
    }

    public TaskDetailSnapshot findTaskDetail(String taskId) {
        if (taskId == null || taskId.isBlank()) return null;
        String id = taskId.trim();
        lock.lock();
        try {
            for (Task t : running) {
                if (t == null) continue;
                if (id.equals(t.id)) return t.toDetailSnapshot();
            }
            for (Task t : pending) {
                if (t == null) continue;
                if (id.equals(t.id)) return t.toDetailSnapshot();
            }
            return completedDetails.get(id);
        } finally {
            lock.unlock();
        }
    }

    private Task enqueue(LlmQueueTaskType type, String providerId, String model, int priority) {
        String id = UUID.randomUUID().toString();
        long s = seq.incrementAndGet();
        long now = System.currentTimeMillis();
        Task task = new Task(id, s, priority, type, providerId == null ? null : providerId.trim(), model == null ? null : model.trim(), now);

        lock.lock();
        try {
            int maxQueueSize = Math.max(1, props.getMaxQueueSize());
            if (pending.size() >= maxQueueSize) {
                throw new IllegalStateException("LLM 调用队列已满，请稍后重试");
            }
            pending.add(task);
            changed.signalAll();
            while (true) {
                int maxConcurrent = Math.max(1, props.getMaxConcurrent());
                Task head = pending.peek();
                if (active < maxConcurrent && head == task) {
                    pending.poll();
                    active++;
                    task.status = LlmQueueTaskStatus.RUNNING;
                    task.startedAtMs = System.currentTimeMillis();
                    running.add(task);
                    changed.signalAll();
                    return task;
                }
                try {
                    changed.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    pending.remove(task);
                    task.status = LlmQueueTaskStatus.CANCELLED;
                    task.error = "interrupted";
                    changed.signalAll();
                    throw new IllegalStateException("LLM 调用排队被中断", ie);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void finishSuccess(Task task, UsageMetrics metrics) {
        TaskSnapshot snap;
        TaskDetailSnapshot detailSnap;
        lock.lock();
        try {
            task.finishedAtMs = System.currentTimeMillis();
            task.status = LlmQueueTaskStatus.DONE;
            applyMetrics(task, metrics);
            running.remove(task);
            active = Math.max(0, active - 1);
            snap = task.toSnapshot();
            pushCompleted(snap);
            pushCompletedDetail(task);
            detailSnap = task.toDetailSnapshot();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        persistCompleted(detailSnap);
        notifyCompleted(snap);
    }

    private void finishFailure(Task task, Exception e) {
        TaskSnapshot snap;
        TaskDetailSnapshot detailSnap;
        lock.lock();
        try {
            task.finishedAtMs = System.currentTimeMillis();
            task.status = LlmQueueTaskStatus.FAILED;
            task.error = e == null ? "error" : String.valueOf(e.getMessage());
            running.remove(task);
            active = Math.max(0, active - 1);
            snap = task.toSnapshot();
            pushCompleted(snap);
            pushCompletedDetail(task);
            detailSnap = task.toDetailSnapshot();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        persistCompleted(detailSnap);
        notifyCompleted(snap);
    }

    private void applyMetrics(Task task, UsageMetrics metrics) {
        if (task == null || metrics == null) return;
        task.tokensIn = metrics.promptTokens();
        task.tokensOut = metrics.tokensOut();
        task.totalTokens = metrics.totalTokens();
        if (task.startedAtMs != null && task.finishedAtMs != null && task.tokensOut != null && task.tokensOut > 0) {
            long durMs = Math.max(1L, task.finishedAtMs - task.startedAtMs);
            task.tokensPerSec = (task.tokensOut * 1000.0) / durMs;
        }
    }

    private void pushCompleted(TaskSnapshot snap) {
        int keep = Math.max(0, props.getKeepCompleted());
        if (keep <= 0) return;
        completed.addFirst(snap);
        while (completed.size() > keep) completed.removeLast();
    }

    private void pushCompletedDetail(Task task) {
        int keep = Math.max(0, props.getKeepCompleted());
        if (keep <= 0 || task == null) return;
        TaskDetailSnapshot snap = task.toDetailSnapshot();
        completedDetails.put(task.id, snap);
        completedDetailOrder.addFirst(task.id);
        while (completedDetailOrder.size() > keep) {
            String old = completedDetailOrder.removeLast();
            if (old != null) completedDetails.remove(old);
        }
    }

    public Runnable subscribeCompleted(Consumer<TaskSnapshot> listener) {
        if (listener == null) return () -> {};
        completedListeners.add(listener);
        return () -> completedListeners.remove(listener);
    }

    private void notifyCompleted(TaskSnapshot snap) {
        if (snap == null) return;
        for (Consumer<TaskSnapshot> l : completedListeners) {
            try {
                l.accept(snap);
            } catch (Exception ignored) {
            }
        }
    }

    private void persistCompleted(TaskDetailSnapshot snap) {
        if (snap == null) return;
        try {
            String truncSuffix = "\n...(truncated)...";

            String input = snap.input();
            String output = snap.output();
            boolean inputTruncated = input != null && input.endsWith(truncSuffix);
            boolean outputTruncated = output != null && output.endsWith(truncSuffix);

            LlmQueueTaskHistoryEntity e = new LlmQueueTaskHistoryEntity();
            e.setTaskId(snap.id());
            e.setSeq(snap.seq());
            e.setPriority(snap.priority());
            e.setType(snap.type());
            e.setStatus(snap.status());
            e.setProviderId(snap.providerId());
            e.setModel(snap.model());
            e.setCreatedAt(snap.createdAt());
            e.setStartedAt(snap.startedAt());
            e.setFinishedAt(snap.finishedAt());
            e.setWaitMs(snap.waitMs());
            e.setDurationMs(snap.durationMs());
            e.setTokensIn(snap.tokensIn());
            e.setTokensOut(snap.tokensOut());
            e.setTotalTokens(snap.totalTokens());
            e.setTokensPerSec(snap.tokensPerSec());
            e.setError(snap.error());
            e.setInput(input);
            e.setOutput(output);
            e.setInputChars(input == null ? null : input.length());
            e.setOutputChars(output == null ? null : output.length());
            e.setInputTruncated(inputTruncated);
            e.setOutputTruncated(outputTruncated);
            e.setUpdatedAt(LocalDateTime.now());

            llmQueueTaskHistoryRepository.save(e);
        } catch (Exception ignored) {
        }
    }
}
