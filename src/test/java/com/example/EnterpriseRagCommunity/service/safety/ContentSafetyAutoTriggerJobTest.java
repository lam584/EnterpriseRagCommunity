package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentSafetyAutoTriggerJobTest {

    @Mock
    private ContentSafetyCircuitBreakerService circuitBreakerService;

    @Mock
    private ModerationPipelineStepRepository pipelineStepRepository;

    @Mock
    private AuditLogWriter auditLogWriter;

    private ContentSafetyAutoTriggerJob newJob() {
        return new ContentSafetyAutoTriggerJob(circuitBreakerService, pipelineStepRepository, auditLogWriter);
    }

    @Test
    void tick_shouldReturn_whenEnabledAndAutoRecoverNotDue() {
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(true, auto(true, 60, 10, 0.9, List.of("REJECT"), "S1", 5, 60));
        when(circuitBreakerService.getConfig()).thenReturn(cfg);
        ContentSafetyAutoTriggerJob job = newJob();
        ReflectionTestUtils.setField(job, "lastAutoEnabledAt", Instant.now());

        job.tick();

        verify(pipelineStepRepository, never()).countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(), any(), anyCollection(), any());
        verify(circuitBreakerService, never()).update(any(), any(), any(), any());
    }

    @Test
    void tick_shouldReturn_whenAutoTriggerDisabledOrMissing() {
        ContentSafetyCircuitBreakerConfigDTO cfg1 = cfg(false, null);
        when(circuitBreakerService.getConfig()).thenReturn(cfg1);
        ContentSafetyAutoTriggerJob job = newJob();

        job.tick();

        verify(pipelineStepRepository, never()).countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(), any(), anyCollection(), any());
    }

    @Test
    void tick_shouldReturn_whenCoolingDown() {
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(false, auto(true, 60, 10, 0.9, List.of("REJECT"), "S2", 300, 0));
        when(circuitBreakerService.getConfig()).thenReturn(cfg);
        ContentSafetyAutoTriggerJob job = newJob();
        ReflectionTestUtils.setField(job, "lastAutoTriggeredAt", Instant.now());

        job.tick();

        verify(pipelineStepRepository, never()).countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(), any(), anyCollection(), any());
    }

    @Test
    void tick_shouldReturn_whenQueryThrows() {
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(false, auto(true, 30, 2, 0.7, List.of("REJECT"), "S3", 0, 0));
        when(circuitBreakerService.getConfig()).thenReturn(cfg);
        when(pipelineStepRepository.countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(LocalDateTime.class), any(), anyCollection(), any(BigDecimal.class)))
                .thenThrow(new RuntimeException("db err"));
        ContentSafetyAutoTriggerJob job = newJob();

        job.tick();

        verify(circuitBreakerService, never()).update(any(), any(), any(), any());
    }

    @Test
    void tick_shouldReturn_whenCountBelowThreshold() {
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(false, auto(true, 30, 10, 0.7, List.of("REJECT"), "S3", 0, 0));
        when(circuitBreakerService.getConfig()).thenReturn(cfg);
        when(pipelineStepRepository.countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(LocalDateTime.class), any(), anyCollection(), any(BigDecimal.class)))
                .thenReturn(3L);
        ContentSafetyAutoTriggerJob job = newJob();

        job.tick();

        verify(circuitBreakerService, never()).update(any(), any(), any(), any());
    }

    @Test
    void tick_shouldAutoTriggerAndWriteAudit_whenThresholdReached() {
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(false, auto(true, null, null, null, List.of("review", "reject", "bad"), "  s3 ", 0, 0));
        when(circuitBreakerService.getConfig()).thenReturn(cfg);
        when(pipelineStepRepository.countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(LocalDateTime.class), any(), anyCollection(), any(BigDecimal.class)))
                .thenReturn(20L);
        ContentSafetyAutoTriggerJob job = newJob();

        job.tick();

        ArgumentCaptor<ContentSafetyCircuitBreakerConfigDTO> nextCaptor = ArgumentCaptor.forClass(ContentSafetyCircuitBreakerConfigDTO.class);
        verify(circuitBreakerService).update(nextCaptor.capture(), any(), anyString(), anyString());
        ContentSafetyCircuitBreakerConfigDTO next = nextCaptor.getValue();
        assertTrue(Boolean.TRUE.equals(next.getEnabled()));
        assertEquals("S3", next.getMode());
        verify(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertNotNull(ReflectionTestUtils.getField(job, "lastAutoEnabledAt"));
    }

    @Test
    void tick_shouldFallbackDecisionsAndTriggerMode_whenInputInvalid() {
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(false, auto(true, 10, 1, 0.5, Arrays.asList(" ", "INVALID"), "   ", 0, 0));
        when(circuitBreakerService.getConfig()).thenReturn(cfg);
        when(pipelineStepRepository.countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(any(LocalDateTime.class), any(), anyCollection(), any(BigDecimal.class)))
                .thenReturn(1L);
        ContentSafetyAutoTriggerJob job = newJob();

        job.tick();

        ArgumentCaptor<ContentSafetyCircuitBreakerConfigDTO> nextCaptor = ArgumentCaptor.forClass(ContentSafetyCircuitBreakerConfigDTO.class);
        verify(circuitBreakerService).update(nextCaptor.capture(), any(), anyString(), anyString());
        assertEquals("S1", nextCaptor.getValue().getMode());
    }

    @Test
    void maybeAutoRecover_shouldCoverAllEarlyReturnBranches_andSuccessAndAuditFailure() {
        ContentSafetyAutoTriggerJob job = newJob();

        ReflectionTestUtils.invokeMethod(job, "maybeAutoRecover", new Object[]{null, null, Instant.now()});
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(true, auto(true, 10, 2, 0.8, List.of("REJECT"), "S1", 0, null));
        ReflectionTestUtils.invokeMethod(job, "maybeAutoRecover", cfg, cfg.getAutoTrigger(), Instant.now());

        cfg.getAutoTrigger().setAutoRecoverSeconds(3);
        ReflectionTestUtils.invokeMethod(job, "maybeAutoRecover", cfg, cfg.getAutoTrigger(), Instant.now());

        ReflectionTestUtils.setField(job, "lastAutoEnabledAt", Instant.now());
        ReflectionTestUtils.invokeMethod(job, "maybeAutoRecover", cfg, cfg.getAutoTrigger(), Instant.now());

        ReflectionTestUtils.setField(job, "lastAutoEnabledAt", Instant.now().minusSeconds(5));
        doThrow(new RuntimeException("audit fail")).when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        ReflectionTestUtils.invokeMethod(job, "maybeAutoRecover", cfg, cfg.getAutoTrigger(), Instant.now());

        verify(circuitBreakerService).update(any(), any(), anyString(), anyString());
    }

    @Test
    void parseVerdicts_shouldCoverNullEmptyAndNullElement() {
        assertTrue(parseVerdictsByReflection(null).isEmpty());
        assertTrue(parseVerdictsByReflection(List.of()).isEmpty());
        List<String> raw = new ArrayList<>();
        raw.add(null);
        assertTrue(parseVerdictsByReflection(raw).isEmpty());
    }

    @Test
    void maybeAutoRecover_shouldSwallowAuditException() {
        ContentSafetyAutoTriggerJob job = newJob();
        ContentSafetyCircuitBreakerConfigDTO cfg = cfg(true, auto(true, 10, 2, 0.8, List.of("REJECT"), "S1", 0, 1));
        ReflectionTestUtils.setField(job, "lastAutoEnabledAt", Instant.now().minusSeconds(2));
        doThrow(new RuntimeException("x")).when(auditLogWriter).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(job, "maybeAutoRecover", cfg, cfg.getAutoTrigger(), Instant.now()));
    }

    @SuppressWarnings("unchecked")
    private static List<?> parseVerdictsByReflection(List<String> raw) {
        try {
            Method m = ContentSafetyAutoTriggerJob.class.getDeclaredMethod("parseVerdicts", List.class);
            m.setAccessible(true);
            return (List<?>) m.invoke(null, raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ContentSafetyCircuitBreakerConfigDTO cfg(boolean enabled, ContentSafetyCircuitBreakerConfigDTO.AutoTrigger at) {
        ContentSafetyCircuitBreakerConfigDTO cfg = ContentSafetyCircuitBreakerService.defaultConfig();
        cfg.setEnabled(enabled);
        cfg.setAutoTrigger(at);
        return cfg;
    }

    private static ContentSafetyCircuitBreakerConfigDTO.AutoTrigger auto(
            boolean enabled,
            Integer windowSeconds,
            Integer thresholdCount,
            Double minConfidence,
            List<String> verdicts,
            String triggerMode,
            Integer coolDownSeconds,
            Integer autoRecoverSeconds
    ) {
        ContentSafetyCircuitBreakerConfigDTO.AutoTrigger at = new ContentSafetyCircuitBreakerConfigDTO.AutoTrigger();
        at.setEnabled(enabled);
        at.setWindowSeconds(windowSeconds);
        at.setThresholdCount(thresholdCount);
        at.setMinConfidence(minConfidence);
        at.setVerdicts(verdicts);
        at.setTriggerMode(triggerMode);
        at.setCoolDownSeconds(coolDownSeconds);
        at.setAutoRecoverSeconds(autoRecoverSeconds);
        return at;
    }
}
