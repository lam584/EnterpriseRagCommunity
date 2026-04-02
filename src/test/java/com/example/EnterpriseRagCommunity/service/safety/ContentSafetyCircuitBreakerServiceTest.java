package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerStatusDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentSafetyCircuitBreakerServiceTest {

    @Mock
    private AppSettingsService appSettingsService;

    private ContentSafetyCircuitBreakerService newService() {
        return new ContentSafetyCircuitBreakerService(appSettingsService, new ObjectMapper());
    }

    @Test
    void reloadFromDbIfPresent_shouldReturn_whenGetStringThrowsOrBlank() {
        ContentSafetyCircuitBreakerService service = newService();
        when(appSettingsService.getString(ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON))
                .thenThrow(new RuntimeException("db"))
                .thenReturn(Optional.of("  "));

        service.reloadFromDbIfPresent();
        service.reloadFromDbIfPresent();
        assertFalse(service.getStatus(10).getPersisted());
    }

    @Test
    void reloadFromDbIfPresent_shouldLoadAndNormalize_whenJsonValid() throws Exception {
        ContentSafetyCircuitBreakerService service = newService();
        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(true);
        cfg.setMode("s3");
        cfg.setMessage(" hi ");
        String raw = new ObjectMapper().writeValueAsString(cfg);
        when(appSettingsService.getString(ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON)).thenReturn(Optional.of(raw));

        service.reloadFromDbIfPresent();

        ContentSafetyCircuitBreakerStatusDTO status = service.getStatus(5);
        assertTrue(Boolean.TRUE.equals(status.getConfig().getEnabled()));
        assertEquals("S3", status.getConfig().getMode());
        assertTrue(Boolean.TRUE.equals(status.getPersisted()));
        assertNotNull(status.getLastPersistAt());
        assertFalse(status.getRecentEvents().isEmpty());
    }

    @Test
    void reloadFromDbIfPresent_shouldIgnore_whenJsonInvalid() {
        ContentSafetyCircuitBreakerService service = newService();
        when(appSettingsService.getString(ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON)).thenReturn(Optional.of("{bad"));

        service.reloadFromDbIfPresent();

        assertFalse(service.getStatus(10).getPersisted());
    }

    @Test
    void update_shouldPersistAndRecordEvent_whenSaveSuccess() {
        ContentSafetyCircuitBreakerService service = newService();
        ContentSafetyCircuitBreakerConfigDTO cfg = ContentSafetyCircuitBreakerService.defaultConfig();
        cfg.setEnabled(true);
        cfg.setMode("s2");

        ContentSafetyCircuitBreakerStatusDTO out = service.update(cfg, 7L, "tester", " reason ");

        assertTrue(Boolean.TRUE.equals(out.getPersisted()));
        assertEquals("tester", out.getUpdatedBy());
        assertEquals(7L, out.getUpdatedByUserId());
        assertFalse(out.getRecentEvents().isEmpty());
        verify(appSettingsService).upsertString(anyString(), anyString());
    }

    @Test
    void update_shouldSetPersistedFalse_whenSaveFails() {
        ContentSafetyCircuitBreakerService service = newService();
        doThrow(new RuntimeException("save failed")).when(appSettingsService).upsertString(anyString(), anyString());

        ContentSafetyCircuitBreakerStatusDTO out = service.update(ContentSafetyCircuitBreakerService.defaultConfig(), null, null, "x");

        assertFalse(out.getPersisted());
    }

    @Test
    void getRecentEvents_shouldHonorLimitAndSkipNulls() {
        ContentSafetyCircuitBreakerService service = newService();

        service.addEvent("A", "m1", Map.of());
        service.addEvent("B", "m2", Map.of());
        service.addEvent(null, "m3", null);

        assertTrue(service.getRecentEvents(0).isEmpty());
        assertEquals(3, service.getRecentEvents(999).size());
    }

    @Test
    void addEvent_shouldTrimQueueTo200() {
        ContentSafetyCircuitBreakerService service = newService();

        for (int i = 0; i < 240; i++) {
            service.addEvent("T" + i, "M" + i, Map.of());
        }

        assertEquals(200, service.getRecentEvents(500).size());
    }

    @Test
    void addBlockedEvent_andMetrics_shouldCountUnknownAndWindow() {
        ContentSafetyCircuitBreakerService service = newService();

        service.addBlockedEvent(null, null, null, "x");
        service.addBlockedEvent(" ", " /p ", " GET ", null);
        service.addBlockedEvent("API", "/x", "POST", "m");
        ContentSafetyCircuitBreakerStatusDTO status = service.getStatus(20);

        assertEquals(3L, status.getRuntimeMetrics().getBlockedTotal());
        assertTrue(status.getRuntimeMetrics().getBlockedLast60s() >= 3L);
        assertTrue(status.getRuntimeMetrics().getBlockedByEntrypoint().containsKey("UNKNOWN"));
        assertTrue(status.getRuntimeMetrics().getBlockedByEntrypoint().containsKey("API"));
    }

    @Test
    void isModeS3Isolation_shouldMatchEnabledModeAndFlags() {
        ContentSafetyCircuitBreakerService service = newService();

        ContentSafetyCircuitBreakerConfigDTO cfg = ContentSafetyCircuitBreakerService.defaultConfig();
        cfg.setEnabled(false);
        cfg.getDependencyIsolation().setMysql(true);
        cfg.getDependencyIsolation().setElasticsearch(true);
        service.update(cfg, null, null, null);
        assertFalse(service.isModeS3WithMysqlIsolation());
        assertFalse(service.isModeS3WithElasticsearchIsolation());

        cfg.setEnabled(true);
        cfg.setMode("S2");
        service.update(cfg, null, null, null);
        assertFalse(service.isModeS3WithMysqlIsolation());
        assertFalse(service.isModeS3WithElasticsearchIsolation());

        cfg.setMode("S3");
        cfg.setDependencyIsolation(null);
        service.update(cfg, null, null, null);
        assertFalse(service.isModeS3WithMysqlIsolation());
        assertFalse(service.isModeS3WithElasticsearchIsolation());

        ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation di = new ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation();
        di.setMysql(true);
        di.setElasticsearch(true);
        cfg.setDependencyIsolation(di);
        service.update(cfg, null, null, null);
        assertTrue(service.isModeS3WithMysqlIsolation());
        assertTrue(service.isModeS3WithElasticsearchIsolation());

        di.setMysql(null);
        di.setElasticsearch(null);
        service.update(cfg, null, null, null);
        assertFalse(service.isModeS3WithMysqlIsolation());
        assertFalse(service.isModeS3WithElasticsearchIsolation());

        di.setMysql(false);
        di.setElasticsearch(false);
        service.update(cfg, null, null, null);
        assertFalse(service.isModeS3WithMysqlIsolation());
        assertFalse(service.isModeS3WithElasticsearchIsolation());
    }

    @Test
    void normalize_shouldHandleNullAndInvalidValues() {
        ContentSafetyCircuitBreakerConfigDTO d = ContentSafetyCircuitBreakerService.normalize(null);
        assertEquals("S1", d.getMode());
        assertFalse(Boolean.TRUE.equals(d.getEnabled()));
        assertNotNull(d.getScope());
        assertNotNull(d.getDependencyIsolation());
        assertNotNull(d.getAutoTrigger());

        ContentSafetyCircuitBreakerConfigDTO in = new ContentSafetyCircuitBreakerConfigDTO();
        in.setEnabled(true);
        in.setMode("bad");
        in.setMessage("   ");
        ContentSafetyCircuitBreakerConfigDTO.Scope s = new ContentSafetyCircuitBreakerConfigDTO.Scope();
        s.setAll(null);
        s.setUserIds(null);
        s.setPostIds(null);
        s.setEntrypoints(null);
        in.setScope(s);
        ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation di = new ContentSafetyCircuitBreakerConfigDTO.DependencyIsolation();
        di.setMysql(null);
        di.setElasticsearch(null);
        in.setDependencyIsolation(di);
        ContentSafetyCircuitBreakerConfigDTO.AutoTrigger at = new ContentSafetyCircuitBreakerConfigDTO.AutoTrigger();
        at.setEnabled(true);
        at.setWindowSeconds(-10);
        at.setThresholdCount(2_000_000);
        at.setMinConfidence(-0.1);
        at.setVerdicts(null);
        at.setTriggerMode("x");
        at.setCoolDownSeconds(100_000);
        at.setAutoRecoverSeconds(-1);
        in.setAutoTrigger(at);

        ContentSafetyCircuitBreakerConfigDTO out = ContentSafetyCircuitBreakerService.normalize(in);
        assertEquals("S1", out.getMode());
        assertNotNull(out.getMessage());
        assertFalse(Boolean.TRUE.equals(out.getScope().getAll()));
        assertEquals(0, out.getScope().getUserIds().size());
        assertFalse(Boolean.TRUE.equals(out.getDependencyIsolation().getMysql()));
        assertFalse(Boolean.TRUE.equals(out.getDependencyIsolation().getElasticsearch()));
        assertEquals(5, out.getAutoTrigger().getWindowSeconds());
        assertEquals(1_000_000, out.getAutoTrigger().getThresholdCount());
        assertEquals(0.0, out.getAutoTrigger().getMinConfidence(), 1e-9);
        assertEquals(List.of("REJECT", "REVIEW"), out.getAutoTrigger().getVerdicts());
        assertEquals("S1", out.getAutoTrigger().getTriggerMode());
        assertEquals(86400, out.getAutoTrigger().getCoolDownSeconds());
        assertEquals(0, out.getAutoTrigger().getAutoRecoverSeconds());
    }

    @Test
    void normalize_shouldCoverUpperBoundAndInfinityAndCopyLists() {
        ContentSafetyCircuitBreakerConfigDTO in = ContentSafetyCircuitBreakerService.defaultConfig();
        in.setMode(" s3 ");
        in.setMessage("  hello  ");
        in.getAutoTrigger().setWindowSeconds(99999);
        in.getAutoTrigger().setThresholdCount(0);
        in.getAutoTrigger().setMinConfidence(Double.POSITIVE_INFINITY);
        in.getAutoTrigger().setTriggerMode("s2");
        in.getAutoTrigger().setCoolDownSeconds(-2);
        in.getAutoTrigger().setAutoRecoverSeconds(9_999_999);
        in.getAutoTrigger().setVerdicts(new ArrayList<>(List.of("A", "B")));
        in.getScope().setUserIds(new ArrayList<>(List.of(1L)));
        in.getScope().setPostIds(new ArrayList<>(List.of(2L)));
        in.getScope().setEntrypoints(new ArrayList<>(List.of("X")));

        ContentSafetyCircuitBreakerConfigDTO out = ContentSafetyCircuitBreakerService.normalize(in);

        assertEquals("S3", out.getMode());
        assertEquals("hello", out.getMessage());
        assertEquals(3600, out.getAutoTrigger().getWindowSeconds());
        assertEquals(1, out.getAutoTrigger().getThresholdCount());
        assertEquals(0.90, out.getAutoTrigger().getMinConfidence(), 1e-9);
        assertEquals("S2", out.getAutoTrigger().getTriggerMode());
        assertEquals(0, out.getAutoTrigger().getCoolDownSeconds());
        assertEquals(7 * 86400, out.getAutoTrigger().getAutoRecoverSeconds());
        assertEquals(List.of("A", "B"), out.getAutoTrigger().getVerdicts());
    }

    @Test
    void normalize_shouldClampMinConfidenceToUpperBound() {
        ContentSafetyCircuitBreakerConfigDTO in = ContentSafetyCircuitBreakerService.defaultConfig();
        in.getAutoTrigger().setMinConfidence(1.5);
        in.getAutoTrigger().setTriggerMode(null);
        in.setMode(null);

        ContentSafetyCircuitBreakerConfigDTO out = ContentSafetyCircuitBreakerService.normalize(in);

        assertEquals(1.0, out.getAutoTrigger().getMinConfidence(), 1e-9);
        assertEquals("S1", out.getAutoTrigger().getTriggerMode());
        assertEquals("S1", out.getMode());
    }

    @Test
    void normalize_shouldUseDefaultMinConfidence_whenNaNOrInfinity() {
        ContentSafetyCircuitBreakerConfigDTO in1 = ContentSafetyCircuitBreakerService.defaultConfig();
        in1.getAutoTrigger().setMinConfidence(Double.NaN);
        ContentSafetyCircuitBreakerConfigDTO out1 = ContentSafetyCircuitBreakerService.normalize(in1);
        assertEquals(0.90, out1.getAutoTrigger().getMinConfidence(), 1e-9);

        ContentSafetyCircuitBreakerConfigDTO in2 = ContentSafetyCircuitBreakerService.defaultConfig();
        in2.getAutoTrigger().setMinConfidence(Double.NEGATIVE_INFINITY);
        ContentSafetyCircuitBreakerConfigDTO out2 = ContentSafetyCircuitBreakerService.normalize(in2);
        assertEquals(0.90, out2.getAutoTrigger().getMinConfidence(), 1e-9);
    }

    @Test
    void countBlockedLast60Seconds_shouldFilterRange() {
        ContentSafetyCircuitBreakerService service = newService();
        long nowSec = System.currentTimeMillis() / 1000L;
        long[] keys = (long[]) ReflectionTestUtils.getField(service, "blockedSecondKeys");
        long[] counts = (long[]) ReflectionTestUtils.getField(service, "blockedSecondCounts");
        for (int i = 0; i < keys.length; i++) {
            keys[i] = nowSec - 200;
            counts[i] = 1;
        }
        keys[1] = nowSec;
        counts[1] = 3;
        keys[2] = nowSec - 1;
        counts[2] = 4;
        keys[3] = nowSec + 1;
        counts[3] = 99;

        long sum = ReflectionTestUtils.invokeMethod(service, "countBlockedLast60Seconds");

        assertEquals(7L, sum);
    }

    @Test
    void getStatus_shouldReturnStructuredFields() {
        ContentSafetyCircuitBreakerService service = newService();
        service.addEvent("X", "msg", null);

        ContentSafetyCircuitBreakerStatusDTO status = service.getStatus(3);

        assertNotNull(status.getConfig());
        assertNotNull(status.getRuntimeMetrics());
        assertEquals(1, status.getRecentEvents().size());
    }

    @Test
    void init_shouldCallReloadFromDbIfPresent() {
        ContentSafetyCircuitBreakerService service = newService();
        when(appSettingsService.getString(ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON)).thenReturn(Optional.of(""));

        service.init();

        verify(appSettingsService).getString(ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON);
    }

    @Test
    void addBlockedEvent_shouldUseDefaultMessage_whenBlankMessage() {
        ContentSafetyCircuitBreakerService service = newService();

        service.addBlockedEvent("EP", "/p", "GET", " ");

        assertEquals("请求被熔断拦截", service.getRecentEvents(1).get(0).getMessage());
    }

    @Test
    void update_shouldTrimReasonToNullInEventDetails() {
        ContentSafetyCircuitBreakerService service = newService();

        service.update(ContentSafetyCircuitBreakerService.defaultConfig(), null, null, "   ");

        Map<String, Object> details = service.getRecentEvents(1).get(0).getDetails();
        assertTrue(details.containsKey("reason"));
        assertNull(details.get("reason"));
    }

    @Test
    void update_shouldNotRequireNonNullActorFields() {
        ContentSafetyCircuitBreakerService service = newService();

        ContentSafetyCircuitBreakerStatusDTO out = service.update(ContentSafetyCircuitBreakerService.defaultConfig(), null, null, null);

        assertNotNull(out.getUpdatedAt());
        assertNull(out.getUpdatedBy());
    }

    @Test
    void reloadFromDbIfPresent_shouldKeepDefault_whenNoValue() {
        ContentSafetyCircuitBreakerService service = newService();
        when(appSettingsService.getString(ContentSafetyCircuitBreakerService.KEY_CONFIG_JSON)).thenReturn(Optional.empty());

        service.reloadFromDbIfPresent();

        assertEquals("S1", service.getConfig().getMode());
        verify(appSettingsService, never()).upsertString(anyString(), anyString());
    }
}
