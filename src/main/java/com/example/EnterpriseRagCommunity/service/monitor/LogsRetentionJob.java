package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LogsRetentionJob {

    private final LogRetentionConfigService logRetentionConfigService;
    private final AuditLogsRepository auditLogsRepository;
    private final AccessLogsRepository accessLogsRepository;

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
            List<AuditLogsEntity> batch = auditLogsRepository.findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(cutoff);
            if (batch.isEmpty()) return;

            if (mode == LogRetentionMode.ARCHIVE_TABLE) {
                LocalDateTime archivedAt = LocalDateTime.now();
                for (AuditLogsEntity e : batch) {
                    e.setArchivedAt(archivedAt);
                }
                auditLogsRepository.saveAll(batch);
            } else {
                auditLogsRepository.deleteAllInBatch(batch);
            }

            processed += batch.size();
        }
    }

    private void processAccessLogs(LocalDateTime cutoff, LogRetentionMode mode, int maxPerRun) {
        int processed = 0;
        while (processed < maxPerRun) {
            List<AccessLogsEntity> batch = accessLogsRepository.findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(cutoff);
            if (batch.isEmpty()) return;

            if (mode == LogRetentionMode.ARCHIVE_TABLE) {
                LocalDateTime archivedAt = LocalDateTime.now();
                for (AccessLogsEntity e : batch) {
                    e.setArchivedAt(archivedAt);
                }
                accessLogsRepository.saveAll(batch);
            } else {
                accessLogsRepository.deleteAllInBatch(batch);
            }

            processed += batch.size();
        }
    }
}

