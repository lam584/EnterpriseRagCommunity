package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmRoutingDecisionEventDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmRoutingDecisionResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmRoutingStateItemDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmRoutingStateResponseDTO;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingTelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class AdminLlmRoutingMonitorController {

    private final LlmRoutingService llmRoutingService;
    private final LlmRoutingTelemetryService llmRoutingTelemetryService;

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @GetMapping("/api/admin/metrics/llm-routing/decisions")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmRoutingDecisionResponseDTO decisions(
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        int lim = limit == null ? 200 : Math.max(1, Math.min(10_000, limit));
        List<LlmRoutingTelemetryService.RoutingDecisionEvent> list = llmRoutingTelemetryService.list(taskType, lim);

        List<AdminLlmRoutingDecisionEventDTO> items = new ArrayList<>(list.size());
        for (LlmRoutingTelemetryService.RoutingDecisionEvent e : list) {
            if (e == null) continue;
            AdminLlmRoutingDecisionEventDTO x = new AdminLlmRoutingDecisionEventDTO();
            x.setTsMs(e.tsMs());
            x.setKind(e.kind());
            x.setTaskType(e.taskType());
            x.setAttempt(e.attempt());
            x.setTaskId(e.taskId());
            x.setProviderId(e.providerId());
            x.setModelName(e.modelName());
            x.setOk(e.ok());
            x.setErrorCode(e.errorCode());
            x.setErrorMessage(e.errorMessage());
            x.setLatencyMs(e.latencyMs());
            x.setApiSource(e.apiSource());
            items.add(x);
        }

        AdminLlmRoutingDecisionResponseDTO out = new AdminLlmRoutingDecisionResponseDTO();
        out.setCheckedAtMs(System.currentTimeMillis());
        out.setItems(items);
        return out;
    }

    @GetMapping(path = "/api/admin/metrics/llm-routing/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public SseEmitter stream(
            @RequestParam(value = "taskType", required = false) String taskType
    ) throws IOException {
        String tt = taskType == null ? null : taskType.trim().toUpperCase(Locale.ROOT);
        SseEmitter emitter = new SseEmitter(0L);

        Runnable unsubscribe = llmRoutingTelemetryService.subscribe((e) -> {
            if (e == null) return;
            if (tt != null && e.taskType() != null && !e.taskType().trim().equalsIgnoreCase(tt)) return;
            AdminLlmRoutingDecisionEventDTO x = new AdminLlmRoutingDecisionEventDTO();
            x.setTsMs(e.tsMs());
            x.setKind(e.kind());
            x.setTaskType(e.taskType());
            x.setAttempt(e.attempt());
            x.setTaskId(e.taskId());
            x.setProviderId(e.providerId());
            x.setModelName(e.modelName());
            x.setOk(e.ok());
            x.setErrorCode(e.errorCode());
            x.setErrorMessage(e.errorMessage());
            x.setLatencyMs(e.latencyMs());
            x.setApiSource(e.apiSource());
            try {
                emitter.send(SseEmitter.event().name("routing").data(x));
            } catch (Exception ex) {
                emitter.complete();
            }
        });

        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(unsubscribe);
        emitter.onError((_e) -> unsubscribe.run());
        emitter.send(SseEmitter.event().name("connected").data("ok"));
        return emitter;
    }

    private static LlmQueueTaskType parseTaskType(String taskType) {
        String s = taskType == null ? "" : taskType.trim();
        if (s.isEmpty()) return LlmQueueTaskType.MULTIMODAL_CHAT;
        String up = s.toUpperCase(Locale.ROOT);
        if ("CHAT".equals(up)) return LlmQueueTaskType.MULTIMODAL_CHAT;
        for (LlmQueueTaskType t : LlmQueueTaskType.values()) {
            if (t.name().equals(up)) return t;
        }
        return LlmQueueTaskType.MULTIMODAL_CHAT;
    }

    @GetMapping("/api/admin/metrics/llm-routing/state")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmRoutingStateResponseDTO state(
            @RequestParam(value = "taskType", required = false) String taskType
    ) {
        LlmQueueTaskType tt = parseTaskType(taskType);
        LlmRoutingService.RuntimeSnapshot snap = llmRoutingService.snapshot(tt);

        AdminLlmRoutingStateResponseDTO out = new AdminLlmRoutingStateResponseDTO();
        out.setCheckedAtMs(snap.checkedAtMs());
        out.setTaskType(tt.name());
        out.setStrategy(snap.policy() == null ? null : enumName(snap.policy().strategy()));
        out.setMaxAttempts(snap.policy() == null ? null : snap.policy().maxAttempts());
        out.setFailureThreshold(snap.policy() == null ? null : snap.policy().failureThreshold());
        out.setCooldownMs(snap.policy() == null ? null : snap.policy().cooldownMs());

        long nowMs = snap.checkedAtMs();
        List<AdminLlmRoutingStateItemDTO> items = new ArrayList<>();
        for (LlmRoutingService.RuntimeTargetState it : snap.items()) {
            if (it == null) continue;
            AdminLlmRoutingStateItemDTO row = new AdminLlmRoutingStateItemDTO();
            row.setTaskType(it.taskType());
            row.setProviderId(it.providerId());
            row.setModelName(it.modelName());
            row.setWeight(it.weight());
            row.setPriority(it.priority());
            row.setQps(it.qps());
            row.setRunningCount(it.runningCount());
            row.setConsecutiveFailures(it.consecutiveFailures());
            row.setCooldownUntilMs(it.cooldownUntilMs());
            row.setCooldownRemainingMs(Math.max(0L, it.cooldownUntilMs() - nowMs));
            row.setCurrentWeight(it.currentWeight());
            row.setLastDispatchAtMs(it.lastDispatchAtMs());
            row.setRateTokens(it.rateTokens());
            row.setLastRefillAtMs(it.lastRefillAtMs());
            items.add(row);
        }
        out.setItems(items);
        return out;
    }
}
