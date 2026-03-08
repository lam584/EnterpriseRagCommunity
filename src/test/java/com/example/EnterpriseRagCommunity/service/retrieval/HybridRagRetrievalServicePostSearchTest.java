package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridRagRetrievalServicePostSearchTest {

    @Test
    void postSearch_shouldNormalizeEndpoint_andSetApiKeyHeader() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });
        when(systemConfigurationService.getConfig("spring.elasticsearch.uris"))
                .thenReturn("mockhttp://es-one:9200/ , mockhttp://es-two:9200");
        when(systemConfigurationService.getConfig("APP_ES_API_KEY"))
                .thenReturn("  abc123  ");

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        JsonNode out = invokePostSearch(svc, "idx_posts", "{\"size\":1}");
        assertNotNull(out);

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertTrue(req.url().toString().startsWith("mockhttp://es-one:9200/idx_posts/_search"));
        assertEquals("application/json", req.headers().get("Content-Type"));
        assertEquals("ApiKey abc123", req.headers().get("Authorization"));
        assertEquals("{\"size\":1}", new String(req.body(), StandardCharsets.UTF_8));
    }

    @Test
    void postSearch_shouldThrow_whenHttpNon2xx() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(401, "{\"message\":\"unauthorized\"}");

        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any(Supplier.class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> s = inv.getArgument(1);
            return s.get();
        });
        when(systemConfigurationService.getConfig(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            if ("spring.elasticsearch.uris".equals(k)) return "mockhttp://es";
            if ("APP_ES_API_KEY".equals(k)) return null;
            return null;
        });

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                systemConfigurationService,
                dependencyIsolationGuard,
                dependencyCircuitBreakerService
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> invokePostSearch(svc, "idx_posts", "{\"size\":1}"));
        assertTrue(ex.getMessage().contains("ES search failed: ES error HTTP 401"));
        assertTrue(ex.getMessage().contains("unauthorized"));
    }

    @SuppressWarnings("unchecked")
    private static JsonNode invokePostSearch(HybridRagRetrievalService svc, String indexName, String body) throws Exception {
        Method m = HybridRagRetrievalService.class.getDeclaredMethod("postSearch", String.class, String.class);
        m.setAccessible(true);
        try {
            return (JsonNode) m.invoke(svc, indexName, body);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw e;
        }
    }
}
