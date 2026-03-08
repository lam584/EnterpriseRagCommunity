package com.example.EnterpriseRagCommunity.service.retrieval.es;

import com.example.EnterpriseRagCommunity.service.es.ElasticsearchIkAnalyzerProbe;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFileAssetsIndexServiceTest {

    @Test
    void ensureIndex_shouldCreateWhenMissing() {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);
        ElasticsearchIkAnalyzerProbe ikProbe = mock(ElasticsearchIkAnalyzerProbe.class);
        DependencyIsolationGuard guard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService cb = mock(DependencyCircuitBreakerService.class);

        doNothing().when(guard).requireElasticsearchAllowed();
        when(cb.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });

        IndexOperations ops = mock(IndexOperations.class);
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        when(ops.exists()).thenReturn(false);

        RagFileAssetsIndexService svc = new RagFileAssetsIndexService(template, ikProbe, guard, cb);

        assertDoesNotThrow(() -> svc.ensureIndex(" idx ", 3, false));

        ArgumentCaptor<Document> capSettings = ArgumentCaptor.forClass(Document.class);
        verify(ops, times(1)).create(capSettings.capture());
        verify(ops, times(1)).putMapping(any(Document.class));
    }

    @Test
    void ensureIndex_shouldThrowWhenExistingDimsMismatch() {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);
        ElasticsearchIkAnalyzerProbe ikProbe = mock(ElasticsearchIkAnalyzerProbe.class);
        DependencyIsolationGuard guard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService cb = mock(DependencyCircuitBreakerService.class);

        doNothing().when(guard).requireElasticsearchAllowed();
        when(cb.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });

        IndexOperations ops = mock(IndexOperations.class);
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        when(ops.exists()).thenReturn(true);
        when(ops.getMapping()).thenReturn(Map.of("properties", Map.of("embedding", Map.of("dims", 2))));

        RagFileAssetsIndexService svc = new RagFileAssetsIndexService(template, ikProbe, guard, cb);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> svc.ensureIndex("idx", 3, false));
        assertTrue(ex.getMessage().contains("mapping mismatch"));
    }

    @Test
    void ensureIndex_shouldRetryWhenIkEnabledAndCreateFails() {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);
        ElasticsearchIkAnalyzerProbe ikProbe = mock(ElasticsearchIkAnalyzerProbe.class);
        DependencyIsolationGuard guard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService cb = mock(DependencyCircuitBreakerService.class);

        doNothing().when(guard).requireElasticsearchAllowed();
        when(cb.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });

        when(ikProbe.isIkSupported()).thenReturn(true);

        IndexOperations ops = mock(IndexOperations.class);
        when(template.indexOps(any(IndexCoordinates.class))).thenReturn(ops);
        when(ops.exists()).thenReturn(false, true);
        when(ops.create(any(Document.class))).thenThrow(new RuntimeException("first")).thenReturn(true);

        RagFileAssetsIndexService svc = new RagFileAssetsIndexService(template, ikProbe, guard, cb);

        assertDoesNotThrow(() -> svc.ensureIndex("idx", 3, true));

        verify(ops, times(2)).create(any(Document.class));
        verify(ops, times(1)).putMapping(any(Document.class));
        verify(ops, times(1)).delete();
    }
}
