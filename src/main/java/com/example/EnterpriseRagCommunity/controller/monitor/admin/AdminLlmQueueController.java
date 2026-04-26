package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueConfigDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueStatusDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDetailDTO;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.LlmQueueMonitorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/metrics/llm-queue")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminLlmQueueController {

    private static final String KEY_MAX_CONCURRENT = "app.ai.queue.maxConcurrent";
    private static final String KEY_MAX_QUEUE_SIZE = "app.ai.queue.maxQueueSize";
    private static final String KEY_KEEP_COMPLETED = "app.ai.queue.keepCompleted";

    private final LlmQueueMonitorService llmQueueMonitorService;
    private final LlmQueueProperties llmQueueProperties;
    private final SystemConfigurationService systemConfigurationService;

    @PostConstruct
    void initFromSystemConfigurations() {
        applyIfPresent(KEY_MAX_CONCURRENT, 1, 1024, llmQueueProperties::setMaxConcurrent);
        applyIfPresent(KEY_MAX_QUEUE_SIZE, 100, 200000, llmQueueProperties::setMaxQueueSize);
        applyIfPresent(KEY_KEEP_COMPLETED, 0, 20000, llmQueueProperties::setKeepCompleted);
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmQueueStatusDTO status(
            @RequestParam(value = "windowSec", required = false) Integer windowSec,
            @RequestParam(value = "limitRunning", required = false) Integer limitRunning,
            @RequestParam(value = "limitPending", required = false) Integer limitPending,
            @RequestParam(value = "limitCompleted", required = false) Integer limitCompleted
    ) {
        return llmQueueMonitorService.query(windowSec, limitRunning, limitPending, limitCompleted);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmQueueTaskDetailDTO taskDetail(@PathVariable("taskId") String taskId) {
        AdminLlmQueueTaskDetailDTO d = llmQueueMonitorService.getTaskDetail(taskId);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或详情已被清理");
        return d;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmQueueConfigDTO getConfig() {
        AdminLlmQueueConfigDTO out = new AdminLlmQueueConfigDTO();
        out.setMaxConcurrent(llmQueueProperties.getMaxConcurrent());
        out.setMaxQueueSize(llmQueueProperties.getMaxQueueSize());
        out.setKeepCompleted(llmQueueProperties.getKeepCompleted());
        return out;
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','write'))")
    public AdminLlmQueueConfigDTO updateConfig(@RequestBody AdminLlmQueueConfigDTO payload) {
        if (payload != null) {
            if (payload.getMaxConcurrent() != null) {
                int v = Math.clamp(payload.getMaxConcurrent(), 1, 1024);
                llmQueueProperties.setMaxConcurrent(v);
                systemConfigurationService.saveConfig(KEY_MAX_CONCURRENT, String.valueOf(v), false, "LLM 调用队列并发上限");
            }
            if (payload.getMaxQueueSize() != null) {
                int v = Math.clamp(payload.getMaxQueueSize(), 100, 200000);
                llmQueueProperties.setMaxQueueSize(v);
                systemConfigurationService.saveConfig(KEY_MAX_QUEUE_SIZE, String.valueOf(v), false, "LLM 调用队列容量上限");
            }
            if (payload.getKeepCompleted() != null) {
                int v = Math.clamp(payload.getKeepCompleted(), 0, 20000);
                llmQueueProperties.setKeepCompleted(v);
                systemConfigurationService.saveConfig(KEY_KEEP_COMPLETED, String.valueOf(v), false, "LLM 调用队列已完成任务保留条数");
            }
        }
        return getConfig();
    }

    private void applyIfPresent(String key, int min, int max, java.util.function.IntConsumer setter) {
        String raw = systemConfigurationService.getConfig(key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            setter.accept(Math.clamp(parsed, min, max));
        } catch (NumberFormatException ignored) {
        }
    }
}
