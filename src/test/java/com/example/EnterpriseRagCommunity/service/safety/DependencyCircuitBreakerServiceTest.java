package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyCircuitBreakerServiceTest {

    @Mock
    private AppSettingsService appSettingsService;

    private DependencyCircuitBreakerService newService() {
        lenient().when(appSettingsService.getLongOrDefault(anyString(), org.mockito.ArgumentMatchers.anyLong())).thenAnswer(inv -> inv.getArgument(1));
        return new DependencyCircuitBreakerService(appSettingsService);
    }

    @Test
    void run_shouldBypassCircuit_whenDependencyIsBlank() {
        DependencyCircuitBreakerService service = newService();

        String out = service.run("   ", () -> "ok");

        assertEquals("ok", out);
    }

    @Test
    void run_shouldThrowUnavailable_whenCircuitOpen() {
        when(appSettingsService.getLongOrDefault("deps.ES.failureThreshold", 5)).thenReturn(1L);
        when(appSettingsService.getLongOrDefault("deps.ES.cooldownSeconds", 30)).thenReturn(60L);
        DependencyCircuitBreakerService service = new DependencyCircuitBreakerService(appSettingsService);
        service.recordFailure("es", new RuntimeException("x"));

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> service.run("es", () -> "x"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void run_shouldResetFailuresAndSetLastSuccess_whenCallSucceeds() {
        when(appSettingsService.getLongOrDefault("deps.MYSQL.failureThreshold", 5)).thenReturn(10L);
        when(appSettingsService.getLongOrDefault("deps.MYSQL.cooldownSeconds", 30)).thenReturn(2L);
        DependencyCircuitBreakerService service = new DependencyCircuitBreakerService(appSettingsService);
        service.recordFailure("mysql", new RuntimeException("f1"));
        service.recordFailure("mysql", new RuntimeException("f2"));

        String out = service.run(" mysql ", () -> "done");

        assertEquals("done", out);
        DependencyCircuitBreakerService.Snapshot snapshot = service.snapshot("MYSQL");
        assertEquals(0, snapshot.failureCount);
        assertNotNull(snapshot.lastSuccessAt);
    }

    @Test
    void run_shouldRethrowUpstreamException_andRecordFailure() {
        DependencyCircuitBreakerService service = newService();
        UpstreamRequestException boom = new UpstreamRequestException(HttpStatus.BAD_GATEWAY, "boom");

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> service.run("mysql", () -> {
            throw boom;
        }));

        assertSame(boom, ex);
        assertNotNull(service.snapshot("mysql").lastFailureAt);
    }

    @Test
    void run_shouldWrapGenericException_withSafeMessage() {
        DependencyCircuitBreakerService service = newService();
        String longMsg = "a".repeat(260);

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> service.run("es", () -> {
            throw new IllegalStateException(longMsg);
        }));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(ex.getMessage().contains("ES 调用失败"));
        assertTrue(ex.getMessage().length() < 240);
    }

    @Test
    void recordFailure_shouldIgnoreBlankDep() {
        DependencyCircuitBreakerService service = newService();

        service.recordFailure(" ", new RuntimeException("x"));

        DependencyCircuitBreakerService.Snapshot snapshot = service.snapshot(" ");
        assertNull(snapshot.dependency);
        assertEquals(0, snapshot.failureCount);
    }

    @Test
    void snapshot_shouldReturnDefaults_whenStateAbsentOrDepNull() {
        DependencyCircuitBreakerService service = newService();

        DependencyCircuitBreakerService.Snapshot s1 = service.snapshot(null);
        DependencyCircuitBreakerService.Snapshot s2 = service.snapshot("missing");

        assertNull(s1.dependency);
        assertEquals("MISSING", s2.dependency);
        assertEquals(0, s2.failureCount);
        assertEquals(5, s2.failureThreshold);
        assertEquals(30, s2.cooldownSeconds);
    }

    @Test
    void recordFailure_shouldOpenCircuit_whenReachThreshold() {
        when(appSettingsService.getLongOrDefault("deps.ES.failureThreshold", 5)).thenReturn(1L);
        when(appSettingsService.getLongOrDefault("deps.ES.cooldownSeconds", 30)).thenReturn(2L);
        DependencyCircuitBreakerService service = new DependencyCircuitBreakerService(appSettingsService);

        service.recordFailure("es", new RuntimeException("x"));
        DependencyCircuitBreakerService.Snapshot snapshot = service.snapshot("es");

        assertEquals(0, snapshot.failureCount);
        assertTrue(snapshot.openUntilMs > System.currentTimeMillis());
    }

    @Test
    void thresholdAndCooldown_shouldClampToRange() {
        when(appSettingsService.getLongOrDefault("deps.A.failureThreshold", 5)).thenReturn(-3L);
        when(appSettingsService.getLongOrDefault("deps.A.cooldownSeconds", 30)).thenReturn(99999L);
        when(appSettingsService.getLongOrDefault("deps.B.failureThreshold", 5)).thenReturn(99999L);
        when(appSettingsService.getLongOrDefault("deps.B.cooldownSeconds", 30)).thenReturn(-9L);
        DependencyCircuitBreakerService service = new DependencyCircuitBreakerService(appSettingsService);

        DependencyCircuitBreakerService.Snapshot a = service.snapshot("a");
        DependencyCircuitBreakerService.Snapshot b = service.snapshot("b");

        assertEquals(0, a.failureThreshold);
        assertEquals(3600, a.cooldownSeconds);
        assertEquals(1000, b.failureThreshold);
        assertEquals(0, b.cooldownSeconds);
    }

    @Test
    void recordFailure_shouldNotOpenCircuit_whenThresholdZero() {
        when(appSettingsService.getLongOrDefault("deps.ES.failureThreshold", 5)).thenReturn(0L);
        when(appSettingsService.getLongOrDefault("deps.ES.cooldownSeconds", 30)).thenReturn(99L);
        DependencyCircuitBreakerService service = new DependencyCircuitBreakerService(appSettingsService);

        service.recordFailure("es", new RuntimeException("x"));
        DependencyCircuitBreakerService.Snapshot snapshot = service.snapshot("es");

        assertEquals(1, snapshot.failureCount);
        assertEquals(0L, snapshot.openUntilMs);
    }

    @Test
    void safeMessage_shouldHandleNullBlankAndLong() {
        DependencyCircuitBreakerService service = newService();

        String n1 = ReflectionTestUtils.invokeMethod(service, "safeMessage", new Object[]{null});
        String n2 = ReflectionTestUtils.invokeMethod(service, "safeMessage", new RuntimeException("   "));
        String n3 = ReflectionTestUtils.invokeMethod(service, "safeMessage", new RuntimeException("x".repeat(300)));

        assertEquals("unknown", n1);
        assertEquals("unknown", n2);
        assertEquals(200, n3.length());

        String n4 = ReflectionTestUtils.invokeMethod(service, "safeMessage", new RuntimeException("ok"));
        assertEquals("ok", n4);
    }
}
