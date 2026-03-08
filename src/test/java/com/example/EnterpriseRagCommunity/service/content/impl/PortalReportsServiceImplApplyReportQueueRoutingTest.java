package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalReportsServiceImplApplyReportQueueRoutingTest {

    @Test
    void applyReportQueueRouting_repNull_throws() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", null, new ModerationQueueEntity()));
    }

    @Test
    void applyReportQueueRouting_repIdNull_throws() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReportsEntity rep = new ReportsEntity();
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, new ModerationQueueEntity()));
    }

    @Test
    void applyReportQueueRouting_queueNull_throws() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, null));
    }

    @Test
    void applyReportQueueRouting_queueIdNull_throws() {
        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        ModerationQueueEntity q = new ModerationQueueEntity();
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q));
    }

    @Test
    void applyReportQueueRouting_routesToHuman_whenPolicyTriggerHits() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setReporterId(55L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);
        rep.setCreatedAt(LocalDateTime.now().minusMinutes(1));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(reportsRepository.countByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.countDistinctReporterIdByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);

        ReportsEntity repInWindow = new ReportsEntity();
        repInWindow.setReporterId(55L);
        when(reportsRepository.findAllByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(List.of(repInWindow));

        UsersEntity u = new UsersEntity();
        u.setId(55L);
        u.setMetadata(Map.of("trust_score", 0.9));
        when(usersRepository.findById(55L)).thenReturn(Optional.of(u));

        Map<String, Object> urgent = new LinkedHashMap<>();
        urgent.put("total_reports_min", 1);
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("window_minutes", 10);
        trigger.put("urgent", urgent);
        trigger.put("standard", Map.of());
        trigger.put("light", Map.of());
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("review_trigger", trigger);

        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setId(10L);
        policy.setContentType(ContentType.POST);
        policy.setPolicyVersion("p1");
        policy.setConfig(cfg);
        policy.setUpdatedAt(LocalDateTime.now());
        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));

        when(moderationQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.HUMAN, q.getStatus());
        assertEquals(QueueStage.HUMAN, q.getCurrentStage());
        verify(moderationQueueRepository).save(eq(q));
        verify(moderationQueueRepository, never()).requeueToAutoWithReviewStage(anyLong(), any(), any(), any(), any());
        verify(moderationRuleAutoRunner, never()).runOnce();
        verify(moderationVecAutoRunner, never()).runOnce();
        verify(moderationLlmAutoRunner, never()).runOnce();
    }

    @Test
    void applyReportQueueRouting_routesToHuman_whenStandardHitsAndUrgentMisses() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setReporterId(55L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);
        rep.setCreatedAt(LocalDateTime.now());

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(reportsRepository.countByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.countDistinctReporterIdByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.findAllByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(null);

        UsersEntity u = new UsersEntity();
        u.setId(55L);
        u.setMetadata(Map.of("trust_score", 0.9));
        when(usersRepository.findById(55L)).thenReturn(Optional.of(u));

        Map<String, Object> urgent = new LinkedHashMap<>();
        urgent.put("total_reports_min", 100);
        Map<String, Object> standard = new LinkedHashMap<>();
        standard.put("total_reports_min", 1);
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("window_minutes", 0);
        trigger.put("urgent", urgent);
        trigger.put("standard", standard);
        trigger.put("light", Map.of());
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("review_trigger", trigger);

        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setContentType(ContentType.POST);
        policy.setConfig(cfg);
        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));

        when(moderationQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.HUMAN, q.getStatus());
        assertEquals(QueueStage.HUMAN, q.getCurrentStage());
        verify(moderationQueueRepository).save(eq(q));
        verify(moderationQueueRepository, never()).requeueToAutoWithReviewStage(anyLong(), any(), any(), any(), any());
        verify(moderationRuleAutoRunner, never()).runOnce();
        verify(moderationVecAutoRunner, never()).runOnce();
        verify(moderationLlmAutoRunner, never()).runOnce();
    }

    @Test
    void applyReportQueueRouting_routesToHuman_whenLightHitsAndOthersMiss() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setReporterId(55L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(reportsRepository.countByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.countDistinctReporterIdByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(1L);
        when(reportsRepository.findAllByTargetTypeAndTargetIdAndCreatedAtAfter(eq(rep.getTargetType()), eq(rep.getTargetId()), any()))
                .thenReturn(List.of(new ReportsEntity()));

        Map<String, Object> urgent = new LinkedHashMap<>();
        urgent.put("total_reports_min", 100);
        Map<String, Object> standard = new LinkedHashMap<>();
        standard.put("total_reports_min", 100);
        Map<String, Object> light = new LinkedHashMap<>();
        light.put("velocity_min_per_window", 1);
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("window_minutes", 10);
        trigger.put("urgent", urgent);
        trigger.put("standard", standard);
        trigger.put("light", light);
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("review_trigger", trigger);

        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setContentType(ContentType.POST);
        policy.setConfig(cfg);
        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));

        when(moderationQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.HUMAN, q.getStatus());
        assertEquals(QueueStage.HUMAN, q.getCurrentStage());
        verify(moderationQueueRepository).save(eq(q));
        verify(moderationQueueRepository, never()).requeueToAutoWithReviewStage(anyLong(), any(), any(), any(), any());
    }

    @Test
    void applyReportQueueRouting_policyConfigInvalid_shouldFallbackAndRequeue() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setContentType(ContentType.POST);
        policy.setConfig(Map.of("review_trigger", "not-a-map"));
        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setUpdatedAt(LocalDateTime.now());
        fb.setReportHumanThreshold(0);
        when(fallbackConfigRepository.findAll()).thenReturn(List.of(fb));

        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(0L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.PENDING, q.getStatus());
        assertEquals(QueueStage.RULE, q.getCurrentStage());
        verify(moderationQueueRepository).requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any());
    }

    @Test
    void applyReportQueueRouting_policyRepoThrows_shouldFallbackAndRequeue() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenThrow(new RuntimeException("ignore"));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setUpdatedAt(LocalDateTime.now());
        fb.setReportHumanThreshold(2);
        when(fallbackConfigRepository.findAll()).thenReturn(List.of(fb));
        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(1L);

        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.PENDING, q.getStatus());
        assertEquals(QueueStage.RULE, q.getCurrentStage());
    }

    @Test
    void applyReportQueueRouting_fallbackRepoThrows_shouldUseDefaultThresholdAndRouteToHuman() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenThrow(new RuntimeException("ignore"));
        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(5L);
        when(moderationQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.HUMAN, q.getStatus());
        assertEquals(QueueStage.HUMAN, q.getCurrentStage());
        verify(moderationQueueRepository).save(eq(q));
    }

    @Test
    void applyReportQueueRouting_reportCountThrows_shouldUseFallbackValueAndRouteToHuman() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setUpdatedAt(LocalDateTime.now());
        fb.setReportHumanThreshold(1);
        when(fallbackConfigRepository.findAll()).thenReturn(List.of(fb));

        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenThrow(new RuntimeException("ignore"));
        when(moderationQueueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.HUMAN, q.getStatus());
        assertEquals(QueueStage.HUMAN, q.getCurrentStage());
        verify(moderationQueueRepository).save(eq(q));
    }

    @Test
    void applyReportQueueRouting_routesToAuto_whenBelowThreshold_andTriggersRunners() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "usersRepository", usersRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", moderationRuleAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", moderationVecAutoRunner);
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", moderationLlmAutoRunner);

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setUpdatedAt(LocalDateTime.now());
        fb.setReportHumanThreshold(10);
        when(fallbackConfigRepository.findAll()).thenReturn(List.of(fb));

        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(1L);

        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(1);

        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(run));
        when(moderationPipelineRunRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("ignore")).when(moderationRuleAutoRunner).runOnce();

        ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q);

        assertEquals(QueueStatus.PENDING, q.getStatus());
        assertEquals(QueueStage.RULE, q.getCurrentStage());
        verify(moderationQueueRepository).requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any());
        verify(moderationRuleAutoRunner).runOnce();
        verify(moderationVecAutoRunner).runOnce();
        verify(moderationLlmAutoRunner).runOnce();
        verify(moderationPipelineRunRepository).save(any(ModerationPipelineRunEntity.class));
    }

    @Test
    void applyReportQueueRouting_requeueFailed_throws() {
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackConfigRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationPipelineRunRepository moderationPipelineRunRepository = mock(ModerationPipelineRunRepository.class);

        PortalReportsServiceImpl svc = new PortalReportsServiceImpl();
        ReflectionTestUtils.setField(svc, "reportsRepository", reportsRepository);
        ReflectionTestUtils.setField(svc, "moderationQueueRepository", moderationQueueRepository);
        ReflectionTestUtils.setField(svc, "fallbackConfigRepository", fallbackConfigRepository);
        ReflectionTestUtils.setField(svc, "policyConfigRepository", policyConfigRepository);
        ReflectionTestUtils.setField(svc, "moderationPipelineRunRepository", moderationPipelineRunRepository);
        ReflectionTestUtils.setField(svc, "moderationRuleAutoRunner", mock(ModerationRuleAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationVecAutoRunner", mock(ModerationVecAutoRunner.class));
        ReflectionTestUtils.setField(svc, "moderationLlmAutoRunner", mock(ModerationLlmAutoRunner.class));

        ReportsEntity rep = new ReportsEntity();
        rep.setId(1L);
        rep.setTargetType(ReportTargetType.POST);
        rep.setTargetId(99L);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(99L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);

        when(policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());
        when(fallbackConfigRepository.findAll()).thenReturn(List.of());
        when(reportsRepository.countByTargetTypeAndTargetId(eq(rep.getTargetType()), eq(rep.getTargetId()))).thenReturn(1L);
        when(moderationQueueRepository.requeueToAutoWithReviewStage(eq(2L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), eq("reported"), any()))
                .thenReturn(0);
        when(moderationPipelineRunRepository.findFirstByQueueIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(svc, "applyReportQueueRouting", rep, q));
    }
}
