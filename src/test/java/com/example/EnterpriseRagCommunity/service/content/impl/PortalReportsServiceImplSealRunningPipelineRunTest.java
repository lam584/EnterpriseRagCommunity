package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplSealRunningPipelineRunTest {

    @Test
    void sealRunningPipelineRun_queueIdNull_returnsEarly() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", new Object[]{null});
        verify(repo, never()).findFirstByQueueIdOrderByCreatedAtDesc(any());
    }

    @Test
    void sealRunningPipelineRun_repoThrows_isSwallowed() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenThrow(new RuntimeException("boom"));

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);
        verify(repo, never()).save(any());
    }

    @Test
    void sealRunningPipelineRun_noRun_returnsEarly() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);
        verify(repo, never()).save(any());
    }

    @Test
    void sealRunningPipelineRun_notRunning_returnsEarly() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.SUCCESS);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(run));

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);
        verify(repo, never()).save(any());
    }

    @Test
    void sealRunningPipelineRun_runningWithoutStartedAt_updatesAndSaves() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        run.setStartedAt(null);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(run));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);

        ArgumentCaptor<ModerationPipelineRunEntity> cap = ArgumentCaptor.forClass(ModerationPipelineRunEntity.class);
        verify(repo).save(cap.capture());
        ModerationPipelineRunEntity saved = cap.getValue();
        assertEquals(ModerationPipelineRunEntity.RunStatus.FAIL, saved.getStatus());
        assertEquals(ModerationPipelineRunEntity.FinalDecision.HUMAN, saved.getFinalDecision());
        assertEquals("REQUEUED", saved.getErrorCode());
        assertEquals("Requeued to auto", saved.getErrorMessage());
        assertNotNull(saved.getEndedAt());
        assertNull(saved.getTotalMs());
    }

    @Test
    void sealRunningPipelineRun_runningWithStartedAt_updatesTotalMs() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now().minusSeconds(2));
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(run));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);

        verify(repo).save(eq(run));
        assertNotNull(run.getTotalMs());
    }

    @Test
    void sealRunningPipelineRun_saveThrows_isSwallowed() {
        ModerationPipelineRunRepository repo = mock(ModerationPipelineRunRepository.class);
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        when(repo.findFirstByQueueIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(run));
        when(repo.save(any())).thenThrow(new RuntimeException("boom"));

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", repo);

        ReflectionTestUtils.invokeMethod(svc, "sealRunningPipelineRun", 1L);
    }
}

