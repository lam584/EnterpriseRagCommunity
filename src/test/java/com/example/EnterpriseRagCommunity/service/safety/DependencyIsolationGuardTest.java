package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.exception.UpstreamRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DependencyIsolationGuardTest {

    @Mock
    private ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    private DependencyIsolationGuard newGuard() {
        return new DependencyIsolationGuard(contentSafetyCircuitBreakerService);
    }

    @Test
    void requireElasticsearchAllowed_shouldThrow_whenIsolated() {
        when(contentSafetyCircuitBreakerService.isModeS3WithElasticsearchIsolation()).thenReturn(true);

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> newGuard().requireElasticsearchAllowed());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void requireElasticsearchAllowed_shouldPass_whenNotIsolated() {
        when(contentSafetyCircuitBreakerService.isModeS3WithElasticsearchIsolation()).thenReturn(false);

        assertDoesNotThrow(() -> newGuard().requireElasticsearchAllowed());
    }

    @Test
    void requireMysqlAllowed_shouldThrow_whenIsolated() {
        when(contentSafetyCircuitBreakerService.isModeS3WithMysqlIsolation()).thenReturn(true);

        UpstreamRequestException ex = assertThrows(UpstreamRequestException.class, () -> newGuard().requireMysqlAllowed());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void requireMysqlAllowed_shouldPass_whenNotIsolated() {
        when(contentSafetyCircuitBreakerService.isModeS3WithMysqlIsolation()).thenReturn(false);

        assertDoesNotThrow(() -> newGuard().requireMysqlAllowed());
    }
}
