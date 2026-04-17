package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.LogRetentionConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.AccessLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.repository.access.AccessLogsRepository;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogsRetentionJobTest {

    @Mock
    private LogRetentionConfigService logRetentionConfigService;

    @Mock
    private AuditLogsRepository auditLogsRepository;

    @Mock
    private AccessLogsRepository accessLogsRepository;

    @InjectMocks
    private LogsRetentionJob logsRetentionJob;

    @Test
    void run_shouldSkipWhenDisabled() {
        when(logRetentionConfigService.getConfig()).thenReturn(new LogRetentionConfigDTO(
                false,
                90,
                LogRetentionMode.ARCHIVE_TABLE,
                5000,
                true,
                true,
                false,
                365
        ));

        logsRetentionJob.run();

        verify(auditLogsRepository, never()).findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(LocalDateTime.class));
        verify(accessLogsRepository, never()).findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(LocalDateTime.class));
    }

    @Test
    void run_shouldApplyPerCategoryAndPurgeArchived() {
        when(logRetentionConfigService.getConfig()).thenReturn(new LogRetentionConfigDTO(
                true,
                30,
                LogRetentionMode.DELETE,
                2000,
                true,
                false,
                true,
                15
        ));

        AuditLogsEntity unarchivedAudit = new AuditLogsEntity();
        AuditLogsEntity archivedAudit = new AuditLogsEntity();
        unarchivedAudit.setId(1L);
        archivedAudit.setId(2L);

        when(auditLogsRepository.findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(LocalDateTime.class)))
                .thenReturn(List.of(unarchivedAudit), List.of());
        when(auditLogsRepository.findTop1000ByArchivedAtBeforeOrderByArchivedAtAscIdAsc(any(LocalDateTime.class)))
                .thenReturn(List.of(archivedAudit), List.of());

        logsRetentionJob.run();

        verify(auditLogsRepository, times(2)).findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(LocalDateTime.class));
        verify(auditLogsRepository).deleteAllInBatch(List.of(unarchivedAudit));

        verify(auditLogsRepository, times(2)).findTop1000ByArchivedAtBeforeOrderByArchivedAtAscIdAsc(any(LocalDateTime.class));
        verify(auditLogsRepository).deleteAllInBatch(List.of(archivedAudit));

        verify(accessLogsRepository, never()).findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(LocalDateTime.class));
        verify(accessLogsRepository, never()).findTop1000ByArchivedAtBeforeOrderByArchivedAtAscIdAsc(any(LocalDateTime.class));
    }

    @Test
    void run_shouldArchiveWhenModeIsArchiveTable() {
        when(logRetentionConfigService.getConfig()).thenReturn(new LogRetentionConfigDTO(
                true,
                30,
                LogRetentionMode.ARCHIVE_TABLE,
                2000,
                false,
                true,
                false,
                365
        ));

        AccessLogsEntity unarchivedAccess = new AccessLogsEntity();
        when(accessLogsRepository.findTop1000ByArchivedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(any(LocalDateTime.class)))
                .thenReturn(List.of(unarchivedAccess), List.of());

        logsRetentionJob.run();

        verify(accessLogsRepository).saveAll(any(List.class));
        verify(accessLogsRepository, never()).deleteAllInBatch(any(List.class));
    }
}
