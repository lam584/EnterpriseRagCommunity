package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyIsolationGuard;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

public class HybridRagRetrievalServiceEsHttpBranchTest {

    @Test
    void postSearch_normalizesEndpoint_andSendsExpectedRequest() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("mockhttp://es/,mockhttp://backup");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k1");
        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

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

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("postSearch", String.class, String.class);
        m.setAccessible(true);
        JsonNode root = (JsonNode) m.invoke(svc, "idx_posts", "{\"size\":1}");
        assertNotNull(root);

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertEquals("mockhttp://es/idx_posts/_search?filter_path=hits.hits._id,hits.hits._score,hits.hits._source", req.url().toString());
        assertEquals("application/json", req.headers().get("Content-Type"));
        assertEquals("ApiKey k1", req.headers().get("Authorization"));
        assertEquals("{\"size\":1}", new String(req.body(), StandardCharsets.UTF_8));
    }

    @Test
    void postSearch_blankApiKey_doesNotSendAuthorizationHeader() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(200, "{\"hits\":{\"hits\":[]}}");

        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("  ");
        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

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

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("postSearch", String.class, String.class);
        m.setAccessible(true);
        m.invoke(svc, "idx_posts", "{\"size\":1}");

        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals(null, req.headers().get("Authorization"));
    }

    @Test
    void postSearch_non2xx_throwsWrappedError() throws Exception {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");

        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("mockhttp://es");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn(null);
        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

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

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("postSearch", String.class, String.class);
        m.setAccessible(true);

        Exception ex = assertThrows(Exception.class, () -> m.invoke(svc, "idx_posts", "{\"size\":1}"));
        Throwable c = ex.getCause() == null ? ex : ex.getCause();
        assertTrue(c.getMessage().contains("ES search failed: ES error HTTP 500"));
    }

    @Test
    void postSearch_errorStreamNull_returnsEmptyObject() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DependencyIsolationGuard dependencyIsolationGuard = mock(DependencyIsolationGuard.class);
        DependencyCircuitBreakerService dependencyCircuitBreakerService = mock(DependencyCircuitBreakerService.class);

        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("http://example.com");
        doNothing().when(dependencyIsolationGuard).requireElasticsearchAllowed();
        when(dependencyCircuitBreakerService.run(eq("ES"), any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        HttpURLConnection conn = mock(HttpURLConnection.class);
        when(conn.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(conn.getResponseCode()).thenReturn(500);
        when(conn.getErrorStream()).thenReturn(null);

        try (MockedConstruction<URL> mockedUrls = mockConstruction(URL.class, (url, context) -> {
            String endpoint = String.valueOf(context.arguments().get(0));
            when(url.getProtocol()).thenReturn(protocol(endpoint));
            when(url.getHost()).thenReturn(host(endpoint));
            when(url.openConnection()).thenReturn(conn);
        })) {
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

            Method m = HybridRagRetrievalService.class.getDeclaredMethod("postSearch", String.class, String.class);
            m.setAccessible(true);
            JsonNode root = (JsonNode) m.invoke(svc, "idx_posts", "{\"size\":1}");
            assertNotNull(root);
            assertTrue(root.isObject());
            assertEquals(0, root.size());
            assertTrue(mockedUrls.constructed().size() >= 1);
        }
    }

    @Test
    void parseEsHits_mapsFields_andToleratesMissingFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree("""
                {"hits":{"hits":[
                  {"_id":"d1","_score":0.5,"_source":{"post_id":10,"chunk_index":2,"board_id":3,"title":"t","content_text":"c"}},
                  {"_id":"d2","_source":{}}
                ]}}
                """);

        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("parseEsHits", JsonNode.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> hits = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, root);

        assertEquals(2, hits.size());
        assertEquals("d1", hits.get(0).getDocId());
        assertEquals(0.5, hits.get(0).getScore(), 1e-9);
        assertEquals(10L, hits.get(0).getPostId());
        assertEquals(2, hits.get(0).getChunkIndex());
        assertEquals(3L, hits.get(0).getBoardId());
        assertEquals("t", hits.get(0).getTitle());
        assertEquals("c", hits.get(0).getContentText());

        assertEquals("d2", hits.get(1).getDocId());
        assertEquals(null, hits.get(1).getScore());
        assertEquals(null, hits.get(1).getPostId());
        assertEquals(null, hits.get(1).getChunkIndex());
        assertEquals(null, hits.get(1).getBoardId());
        assertEquals(null, hits.get(1).getTitle());
        assertEquals(null, hits.get(1).getContentText());
    }

    @Test
    void parseEsHits_nullRoot_returnsEmptyList() throws Exception {
        HybridRagRetrievalService svc = new HybridRagRetrievalService(
                null,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        Method m = HybridRagRetrievalService.class.getDeclaredMethod("parseEsHits", JsonNode.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HybridRagRetrievalService.DocHit> hits = (List<HybridRagRetrievalService.DocHit>) m.invoke(svc, new Object[] { null });

        assertNotNull(hits);
        assertEquals(0, hits.size());
    }

    private static String protocol(String endpoint) {
        int i = endpoint.indexOf("://");
        if (i < 0) return "";
        return endpoint.substring(0, i);
    }

    private static String host(String endpoint) {
        int i = endpoint.indexOf("://");
        if (i < 0) return "";
        String rest = endpoint.substring(i + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }
}

