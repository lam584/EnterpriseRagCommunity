package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueSampleDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueStatusDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDetailDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDTO;
import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmQueueMonitorService {

    private record Sample(long tsMs, int queueLen, int running, double tokensPerSec) {
    }

    private final LlmCallQueueService llmCallQueueService;
    private final LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;
    private final LlmQueueProperties llmQueueProperties;

    private final Object lock = new Object();
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long lastSeenCompletedSeq = 0;
    private double lastNonZeroTokensPerSec = 0.0;
    private long lastNonZeroTokensPerSecAtMs = 0L;
    private long lastRunningTokensOutSum = 0L;
    private long lastRunningTokensOutAtMs = 0L;

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        LlmCallQueueService.QueueSnapshot snap = llmCallQueueService.snapshot(200, 0, 200);
        int queueLen = snap.pendingCount();
        int running = snap.runningCount();

        double instantTps = 0.0;
        long maxSeen = lastSeenCompletedSeq;
        long sumDurMs = 0L;
        long sumTokensOut = 0L;
        int cnt = 0;
        double sum = 0.0;
        for (LlmCallQueueService.TaskSnapshot t : snap.recentCompleted()) {
            if (t == null) continue;
            long seq = t.getSeq();
            if (seq <= lastSeenCompletedSeq) break;
            Integer tokensOut = t.getTokensOut();
            Long durMs = t.getDurationMs();
            if (tokensOut != null && tokensOut > 0 && durMs != null && durMs > 0) {
                sumTokensOut += tokensOut;
                sumDurMs += durMs;
            } else if (t.getTokensPerSec() != null && t.getTokensPerSec() > 0) {
                sum += t.getTokensPerSec();
                cnt++;
            }
            if (seq > maxSeen) maxSeen = seq;
        }
        if (sumDurMs > 0) instantTps = (sumTokensOut * 1000.0) / sumDurMs;
        else if (cnt > 0) instantTps = sum / cnt;

        long now = System.currentTimeMillis();
        long runningTokensOutSum = 0L;
        for (LlmCallQueueService.TaskSnapshot t : snap.running()) {
            if (t == null) continue;
            Integer out = t.getTokensOut();
            if (out != null && out > 0) runningTokensOutSum += out;
        }

        double runningTps = 0.0;
        long prevSum;
        long prevAt;
        synchronized (lock) {
            prevSum = lastRunningTokensOutSum;
            prevAt = lastRunningTokensOutAtMs;
            lastRunningTokensOutSum = runningTokensOutSum;
            lastRunningTokensOutAtMs = now;
        }
        if (running > 0 && prevAt > 0 && now > prevAt) {
            long delta = runningTokensOutSum - prevSum;
            long dtMs = now - prevAt;
            if (delta > 0 && dtMs > 0) runningTps = (delta * 1000.0) / dtMs;
        }

        if (runningTps > 0) instantTps = runningTps;
        double tps = smoothTokensPerSec(instantTps, running, now);
        synchronized (lock) {
            samples.addLast(new Sample(now, queueLen, running, tps));
            int keep = 3600;
            while (samples.size() > keep) samples.removeFirst();
            lastSeenCompletedSeq = Math.max(lastSeenCompletedSeq, maxSeen);
        }
    }

    private double smoothTokensPerSec(double instantTps, int running, long nowMs) {
        if (instantTps > 0) {
            synchronized (lock) {
                lastNonZeroTokensPerSec = instantTps;
                lastNonZeroTokensPerSecAtMs = nowMs;
            }
            return instantTps;
        }

        if (running <= 0) return 0.0;

        double lastTps;
        long lastAt;
        synchronized (lock) {
            lastTps = lastNonZeroTokensPerSec;
            lastAt = lastNonZeroTokensPerSecAtMs;
        }
        if (!(lastTps > 0) || lastAt <= 0) return 0.0;

        long ageMs = nowMs - lastAt;
        if (ageMs <= 0) return lastTps;
        if (ageMs <= 60_000L) return lastTps;
        if (ageMs >= 300_000L) return 0.0;

        double ageSecBeyondHold = (ageMs - 60_000L) / 1000.0;
        double tauSec = 60.0;
        double decayed = lastTps * Math.exp(-ageSecBeyondHold / tauSec);
        return decayed < 0.05 ? 0.0 : decayed;
    }

    public AdminLlmQueueStatusDTO query(Integer windowSec, Integer limitRunning, Integer limitPending, Integer limitCompleted) {
        int win = windowSec == null ? 300 : Math.max(10, Math.min(3600, windowSec));
        int runLim = limitRunning == null ? 50 : Math.max(0, Math.min(20000, limitRunning));
        int pendLim = limitPending == null ? 200 : Math.max(0, Math.min(200000, limitPending));
        int defaultDoneLim = llmQueueProperties.getKeepCompleted();
        if (defaultDoneLim <= 0) defaultDoneLim = 200;
        int doneLim = limitCompleted == null ? defaultDoneLim : limitCompleted;
        doneLim = Math.max(1, Math.min(20000, doneLim));

        LlmCallQueueService.QueueSnapshot snap = llmCallQueueService.snapshot(runLim, pendLim, doneLim);

        AdminLlmQueueStatusDTO out = new AdminLlmQueueStatusDTO();
        out.setMaxConcurrent(snap.maxConcurrent());
        out.setRunningCount(snap.runningCount());
        out.setPendingCount(snap.pendingCount());
        out.setRunning(mapTasks(snap.running()));
        out.setPending(mapTasks(snap.pending()));
        out.setRecentCompleted(mergeRecentCompleted(snap.recentCompleted(), doneLim));
        out.setSamples(readSamples(win));
        return out;
    }

    public AdminLlmQueueTaskDetailDTO getTaskDetail(String taskId) {
        LlmCallQueueService.TaskDetailSnapshot t = llmCallQueueService.findTaskDetail(taskId);
        if (t != null) return mapDetail(t);

        LlmQueueTaskHistoryEntity e = llmQueueTaskHistoryRepository.findById(taskId).orElse(null);
        if (e == null) return null;
        return mapDetail(e);
    }

    private List<AdminLlmQueueTaskDTO> mapTasks(List<LlmCallQueueService.TaskSnapshot> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<AdminLlmQueueTaskDTO> out = new ArrayList<>(list.size());
        for (LlmCallQueueService.TaskSnapshot t : list) {
            if (t == null) continue;
            AdminLlmQueueTaskDTO d = new AdminLlmQueueTaskDTO();
            d.setId(t.getId());
            d.setSeq(t.getSeq());
            d.setPriority(t.getPriority());
            d.setType(t.getType());
            d.setStatus(t.getStatus());
            d.setProviderId(t.getProviderId());
            d.setModel(t.getModel());
            d.setCreatedAt(t.createdAt());
            d.setStartedAt(t.startedAt());
            d.setFinishedAt(t.finishedAt());
            d.setWaitMs(t.getWaitMs());
            d.setDurationMs(t.getDurationMs());
            d.setTokensIn(t.getTokensIn());
            d.setTokensOut(t.getTokensOut());
            d.setTotalTokens(t.getTotalTokens());
            d.setTokensPerSec(t.getTokensPerSec());
            d.setError(t.getError());
            out.add(d);
        }
        return out;
    }

    private List<AdminLlmQueueTaskDTO> mergeRecentCompleted(List<LlmCallQueueService.TaskSnapshot> inMemory, int limit) {
        int lim = Math.max(1, limit);
        Map<String, AdminLlmQueueTaskDTO> byId = new LinkedHashMap<>();

        for (AdminLlmQueueTaskDTO d : mapTasks(inMemory)) {
            if (d == null || d.getId() == null) continue;
            byId.put(d.getId(), d);
        }

        int remaining = lim - byId.size();
        if (remaining > 0) {
            List<LlmQueueTaskHistoryEntity> db = llmQueueTaskHistoryRepository.findByFinishedAtIsNotNullOrderByFinishedAtDesc(
                    PageRequest.of(0, remaining)
            );
            for (LlmQueueTaskHistoryEntity e : db) {
                if (e == null || e.getTaskId() == null) continue;
                byId.putIfAbsent(e.getTaskId(), mapTask(e));
                if (byId.size() >= lim) break;
            }
        }

        List<AdminLlmQueueTaskDTO> out = new ArrayList<>(byId.values());
        Comparator<AdminLlmQueueTaskDTO> order = Comparator
                .comparing((AdminLlmQueueTaskDTO x) -> x.getFinishedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(Comparator.comparing((AdminLlmQueueTaskDTO x) -> x.getSeq(), Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        out.sort(order);
        if (out.size() > lim) return out.subList(0, lim);
        return out;
    }

    private AdminLlmQueueTaskDTO mapTask(LlmQueueTaskHistoryEntity e) {
        if (e == null) return null;
        AdminLlmQueueTaskDTO d = new AdminLlmQueueTaskDTO();
        d.setId(e.getTaskId());
        d.setSeq(e.getSeq());
        d.setPriority(e.getPriority());
        d.setType(e.getType());
        d.setStatus(e.getStatus());
        d.setProviderId(e.getProviderId());
        d.setModel(e.getModel());
        d.setCreatedAt(e.getCreatedAt());
        d.setStartedAt(e.getStartedAt());
        d.setFinishedAt(e.getFinishedAt());
        d.setWaitMs(e.getWaitMs());
        d.setDurationMs(e.getDurationMs());
        d.setTokensIn(e.getTokensIn());
        d.setTokensOut(e.getTokensOut());
        d.setTotalTokens(e.getTotalTokens());
        d.setTokensPerSec(e.getTokensPerSec());
        d.setError(e.getError());
        return d;
    }

    private AdminLlmQueueTaskDetailDTO mapDetail(LlmCallQueueService.TaskDetailSnapshot t) {
        AdminLlmQueueTaskDetailDTO d = new AdminLlmQueueTaskDetailDTO();
        d.setId(t.id());
        d.setSeq(t.seq());
        d.setPriority(t.priority());
        d.setType(t.type());
        d.setStatus(t.status());
        d.setProviderId(t.providerId());
        d.setModel(t.model());
        d.setCreatedAt(t.createdAt());
        d.setStartedAt(t.startedAt());
        d.setFinishedAt(t.finishedAt());
        d.setWaitMs(t.waitMs());
        d.setDurationMs(t.durationMs());
        d.setTokensIn(t.tokensIn());
        d.setTokensOut(t.tokensOut());
        d.setTotalTokens(t.totalTokens());
        d.setTokensPerSec(t.tokensPerSec());
        d.setError(t.error());
        d.setInput(t.input());
        d.setOutput(t.output());
        return d;
    }

    private AdminLlmQueueTaskDetailDTO mapDetail(LlmQueueTaskHistoryEntity e) {
        AdminLlmQueueTaskDetailDTO d = new AdminLlmQueueTaskDetailDTO();
        d.setId(e.getTaskId());
        d.setSeq(e.getSeq());
        d.setPriority(e.getPriority());
        d.setType(e.getType());
        d.setStatus(e.getStatus());
        d.setProviderId(e.getProviderId());
        d.setModel(e.getModel());
        d.setCreatedAt(e.getCreatedAt());
        d.setStartedAt(e.getStartedAt());
        d.setFinishedAt(e.getFinishedAt());
        d.setWaitMs(e.getWaitMs());
        d.setDurationMs(e.getDurationMs());
        d.setTokensIn(e.getTokensIn());
        d.setTokensOut(e.getTokensOut());
        d.setTotalTokens(e.getTotalTokens());
        d.setTokensPerSec(e.getTokensPerSec());
        d.setError(e.getError());
        d.setInput(e.getInput());
        d.setOutput(e.getOutput());
        return d;
    }

    private List<AdminLlmQueueSampleDTO> readSamples(int windowSec) {
        long cutoff = System.currentTimeMillis() - windowSec * 1000L;
        List<AdminLlmQueueSampleDTO> out = new ArrayList<>();
        synchronized (lock) {
            for (Sample s : samples) {
                if (s.tsMs < cutoff) continue;
                AdminLlmQueueSampleDTO d = new AdminLlmQueueSampleDTO();
                d.setTs(toLocalDateTime(s.tsMs));
                d.setQueueLen(s.queueLen);
                d.setRunning(s.running);
                d.setTokensPerSec(s.tokensPerSec);
                out.add(d);
            }
        }
        return out;
    }

    private static LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
