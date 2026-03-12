package com.example.EnterpriseRagCommunity.service.ai.admin;

import com.example.EnterpriseRagCommunity.dto.ai.ChatContextGovernanceConfigDTO;
import com.example.EnterpriseRagCommunity.repository.ai.AiChatContextEventsRepository;
import com.example.EnterpriseRagCommunity.service.ai.ChatContextGovernanceConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatContextEventsRetentionJobTest {

    @Test
    void run_whenConfigIsNull_shouldSkipDelete() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventsRetentionJob job = new ChatContextEventsRetentionJob(configService, repository);
        when(configService.getConfigOrDefault()).thenReturn(null);

        job.run();

        verify(repository, never()).deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_whenLogDisabled_shouldSkipDelete() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventsRetentionJob job = new ChatContextEventsRetentionJob(configService, repository);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setLogEnabled(false);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        job.run();

        verify(repository, never()).deleteByCreatedAtBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_whenLogEnabledAndDaysNull_shouldUseDefault30Days() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventsRetentionJob job = new ChatContextEventsRetentionJob(configService, repository);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setLogEnabled(true);
        cfg.setLogMaxDays(null);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime before = LocalDateTime.now();
        job.run();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(captor.capture());
        assertCutoffInRange(captor.getValue(), before, after, 30);
    }

    @Test
    void run_whenLogEnabledAndDaysTooSmall_shouldClampToOneDay() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventsRetentionJob job = new ChatContextEventsRetentionJob(configService, repository);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setLogEnabled(true);
        cfg.setLogMaxDays(0);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime before = LocalDateTime.now();
        job.run();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(captor.capture());
        assertCutoffInRange(captor.getValue(), before, after, 1);
    }

    @Test
    void run_whenLogEnabledAndDaysTooLarge_shouldClampTo3650Days() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventsRetentionJob job = new ChatContextEventsRetentionJob(configService, repository);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setLogEnabled(true);
        cfg.setLogMaxDays(999999);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime before = LocalDateTime.now();
        job.run();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(captor.capture());
        assertCutoffInRange(captor.getValue(), before, after, 3650);
    }

    @Test
    void run_whenLogEnabledAndDaysNormal_shouldUseProvidedDays() {
        ChatContextGovernanceConfigService configService = mock(ChatContextGovernanceConfigService.class);
        AiChatContextEventsRepository repository = mock(AiChatContextEventsRepository.class);
        ChatContextEventsRetentionJob job = new ChatContextEventsRetentionJob(configService, repository);
        ChatContextGovernanceConfigDTO cfg = new ChatContextGovernanceConfigDTO();
        cfg.setLogEnabled(true);
        cfg.setLogMaxDays(45);
        when(configService.getConfigOrDefault()).thenReturn(cfg);

        LocalDateTime before = LocalDateTime.now();
        job.run();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteByCreatedAtBefore(captor.capture());
        assertCutoffInRange(captor.getValue(), before, after, 45);
    }

    private static void assertCutoffInRange(LocalDateTime actualCutoff, LocalDateTime before, LocalDateTime after, int days) {
        LocalDateTime expectedLower = before.minusDays(days).minusSeconds(2);
        LocalDateTime expectedUpper = after.minusDays(days).plusSeconds(2);
        assertFalse(actualCutoff.isBefore(expectedLower));
        assertFalse(actualCutoff.isAfter(expectedUpper));
        assertTrue(actualCutoff.isBefore(after));
    }
}
