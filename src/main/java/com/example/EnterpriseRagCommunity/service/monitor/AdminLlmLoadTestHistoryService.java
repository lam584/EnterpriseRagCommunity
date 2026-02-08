package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestHistoryRecordDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestHistoryUpsertRequestDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminLlmLoadTestHistoryService {

    private final LlmLoadTestRunHistoryRepository repository;
    private final LlmLoadTestRunDetailRepository detailRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminLlmLoadTestHistoryRecordDTO upsert(AdminLlmLoadTestHistoryUpsertRequestDTO req) {
        if (req == null) throw new IllegalArgumentException("参数不能为空");
        String runId = safeTrim(req.getRunId());
        JsonNode summary = req.getSummary();

        if (runId == null || runId.isEmpty()) {
            runId = safeTrim(summary != null && summary.hasNonNull("runId") ? summary.get("runId").asText(null) : null);
        }
        if (runId == null || runId.isEmpty()) throw new IllegalArgumentException("runId 不能为空");
        if (summary == null || summary.isNull()) throw new IllegalArgumentException("summary 不能为空");

        LlmLoadTestRunHistoryEntity e = repository.findById(runId).orElseGet(LlmLoadTestRunHistoryEntity::new);
        e.setRunId(runId);

        String createdAtRaw = summary.hasNonNull("createdAt") ? summary.get("createdAt").asText(null) : null;
        LocalDateTime createdAt = parseIsoDateTimeOrNow(createdAtRaw);
        e.setCreatedAt(createdAt);

        JsonNode cfg = summary.get("config");
        e.setProviderId(safeTrim(cfg != null && cfg.hasNonNull("providerId") ? cfg.get("providerId").asText(null) : null));
        e.setModel(safeTrim(cfg != null && cfg.hasNonNull("model") ? cfg.get("model").asText(null) : null));
        e.setStream(cfg != null && cfg.hasNonNull("stream") ? cfg.get("stream").asBoolean() : null);
        e.setEnableThinking(cfg != null && cfg.hasNonNull("enableThinking") ? cfg.get("enableThinking").asBoolean() : null);
        e.setTimeoutMs(cfg != null && cfg.hasNonNull("timeoutMs") ? cfg.get("timeoutMs").asInt() : null);
        e.setRetries(cfg != null && cfg.hasNonNull("retries") ? cfg.get("retries").asInt() : null);
        e.setRetryDelayMs(cfg != null && cfg.hasNonNull("retryDelayMs") ? cfg.get("retryDelayMs").asInt() : null);

        e.setSummaryJson(writeJsonQuietly(summary));
        e.setUpdatedAt(LocalDateTime.now());
        LlmLoadTestRunHistoryEntity saved = repository.save(e);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AdminLlmLoadTestHistoryRecordDTO> list(int limit) {
        int size = Math.max(1, Math.min(200, limit));
        List<LlmLoadTestRunHistoryEntity> rows = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size));
        List<AdminLlmLoadTestHistoryRecordDTO> out = new ArrayList<>(rows.size());
        for (LlmLoadTestRunHistoryEntity e : rows) out.add(toDto(e));
        return out;
    }

    @Transactional
    public void delete(String runId) {
        String rid = safeTrim(runId);
        if (rid == null || rid.isEmpty()) throw new IllegalArgumentException("runId 不能为空");
        try {
            detailRepository.deleteByRunId(rid);
        } catch (Exception ignored) {
        }
        repository.deleteById(rid);
    }

    private AdminLlmLoadTestHistoryRecordDTO toDto(LlmLoadTestRunHistoryEntity e) {
        if (e == null) return null;
        AdminLlmLoadTestHistoryRecordDTO d = new AdminLlmLoadTestHistoryRecordDTO();
        d.setRunId(e.getRunId());
        d.setCreatedAt(e.getCreatedAt());
        d.setProviderId(e.getProviderId());
        d.setModel(e.getModel());
        d.setStream(e.getStream());
        d.setEnableThinking(e.getEnableThinking());
        d.setRetries(e.getRetries());
        d.setRetryDelayMs(e.getRetryDelayMs());
        d.setTimeoutMs(e.getTimeoutMs());
        d.setSummary(readJsonQuietly(e.getSummaryJson()));
        return d;
    }

    private String writeJsonQuietly(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("summary 序列化失败");
        }
    }

    private JsonNode readJsonQuietly(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseIsoDateTimeOrNow(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            Instant i = Instant.parse(raw.trim());
            return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String safeTrim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
