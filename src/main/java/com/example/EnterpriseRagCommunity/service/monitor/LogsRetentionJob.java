package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsArchiveEntity;
import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsArchiveEntity;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsArchiveRepository;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsArchiveRepository;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LogsRetentionJob {

    private final LogRetentionConfigService logRetentionConfigService;
    private final AuditLogsRepository auditLogsRepository;
    private final AuditLogsArchiveRepository auditLogsArchiveRepository;
    private final AccessLogsRepository accessLogsRepository;
    private final AccessLogsArchiveRepository accessLogsArchiveRepository;

    @Scheduled(cron = "${monitor.logs.retention.cron:0 5 * * * *}")
    @Transactional
    public void run() {
        var cfg = logRetentionConfigService.getConfig();
        if (!cfg.enabled()) return;

        LocalDateTime cutoff = LocalDateTime.now().minusDays(cfg.keepDays());
        LogRetentionMode mode = cfg.mode();

        int maxPerRun = 5000;
        processAuditLogs(cutoff, mode, maxPerRun);
        processAccessLogs(cutoff, mode, maxPerRun);
    }

    private void processAuditLogs(LocalDateTime cutoff, LogRetentionMode mode, int maxPerRun) {
        int processed = 0;
        while (processed < maxPerRun) {
            List<AuditLogsEntity> batch = auditLogsRepository.findTop1000ByCreatedAtBeforeOrderByCreatedAtAscIdAsc(cutoff);
            if (batch.isEmpty()) return;

            if (mode == LogRetentionMode.ARCHIVE_TABLE) {
                LocalDateTime archivedAt = LocalDateTime.now();
                List<AuditLogsArchiveEntity> arcs = new ArrayList<>(batch.size());
                for (AuditLogsEntity e : batch) {
                    AuditLogsArchiveEntity a = new AuditLogsArchiveEntity();
                    a.setTenantId(e.getTenantId());
                    a.setActorUserId(e.getActorUserId());
                    a.setAction(e.getAction());
                    a.setEntityType(e.getEntityType());
                    a.setEntityId(e.getEntityId());
                    a.setResult(e.getResult());
                    a.setCreatedAt(e.getCreatedAt());
                    a.setArchivedAt(archivedAt);
                    a.setDetails(appendSourceId(e.getDetails(), e.getId()));
                    arcs.add(a);
                }
                auditLogsArchiveRepository.saveAll(arcs);
            }

            auditLogsRepository.deleteAllInBatch(batch);
            processed += batch.size();
        }
    }

    private void processAccessLogs(LocalDateTime cutoff, LogRetentionMode mode, int maxPerRun) {
        int processed = 0;
        while (processed < maxPerRun) {
            List<AccessLogsEntity> batch = accessLogsRepository.findTop1000ByCreatedAtBeforeOrderByCreatedAtAscIdAsc(cutoff);
            if (batch.isEmpty()) return;

            if (mode == LogRetentionMode.ARCHIVE_TABLE) {
                LocalDateTime archivedAt = LocalDateTime.now();
                List<AccessLogsArchiveEntity> arcs = new ArrayList<>(batch.size());
                for (AccessLogsEntity e : batch) {
                    AccessLogsArchiveEntity a = new AccessLogsArchiveEntity();
                    a.setTenantId(e.getTenantId());
                    a.setUserId(e.getUserId());
                    a.setUsername(e.getUsername());
                    a.setMethod(e.getMethod());
                    a.setPath(e.getPath());
                    a.setQueryString(e.getQueryString());
                    a.setStatusCode(e.getStatusCode());
                    a.setLatencyMs(e.getLatencyMs());
                    a.setClientIp(e.getClientIp());
                    a.setClientPort(e.getClientPort());
                    a.setServerIp(e.getServerIp());
                    a.setServerPort(e.getServerPort());
                    a.setScheme(e.getScheme());
                    a.setHost(e.getHost());
                    a.setRequestId(e.getRequestId());
                    a.setTraceId(e.getTraceId());
                    a.setUserAgent(e.getUserAgent());
                    a.setReferer(e.getReferer());
                    a.setCreatedAt(e.getCreatedAt());
                    a.setArchivedAt(archivedAt);
                    a.setDetails(appendSourceId(e.getDetails(), e.getId()));
                    arcs.add(a);
                }
                accessLogsArchiveRepository.saveAll(arcs);
            }

            accessLogsRepository.deleteAllInBatch(batch);
            processed += batch.size();
        }
    }

    private static Map<String, Object> appendSourceId(Map<String, Object> details, Long sourceId) {
        if (sourceId == null) return details;
        Map<String, Object> out = new LinkedHashMap<>();
        if (details != null) out.putAll(details);
        out.putIfAbsent("sourceId", sourceId);
        return out;
    }
}

