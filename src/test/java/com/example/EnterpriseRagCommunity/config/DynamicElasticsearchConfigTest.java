package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicElasticsearchConfigTest {

    @Test
    void restClient_when_uris_blank_and_api_key_missing_should_still_initialize() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("   ");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("");

        DynamicElasticsearchConfig cfg = new DynamicElasticsearchConfig(systemConfigurationService);

        RestClient proxy = cfg.restClient();
        assertNotNull(proxy);

        cfg.shutdown();
    }

    @Test
    void restClient_when_uris_invalid_should_fallback_to_default_host() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn(" , , not-a-uri , ");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");

        DynamicElasticsearchConfig cfg = new DynamicElasticsearchConfig(systemConfigurationService);

        RestClient proxy = cfg.restClient();
        assertNotNull(proxy);

        cfg.shutdown();
    }

    @Test
    void refresh_when_target_source_null_should_initialize_then_refresh() {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("http://localhost:9200");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");

        DynamicElasticsearchConfig cfg = new DynamicElasticsearchConfig(systemConfigurationService);

        cfg.refresh();

        cfg.shutdown();
    }

    @Test
    void refresh_should_swallow_close_exception_of_old_client() throws Exception {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn("http://localhost:9200");
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn("k");

        DynamicElasticsearchConfig cfg = new DynamicElasticsearchConfig(systemConfigurationService);

        RestClient oldClient = mock(RestClient.class);
        doThrow(new RuntimeException("close boom")).when(oldClient).close();
        HotSwappableTargetSource ts = new HotSwappableTargetSource(oldClient);
        ReflectionTestUtils.setField(cfg, "targetSource", ts);
        @SuppressWarnings("unchecked")
        AtomicReference<RestClient> ref = (AtomicReference<RestClient>) ReflectionTestUtils.getField(cfg, "currentClient");
        ref.set(oldClient);

        cfg.refresh();

        cfg.shutdown();
    }

    @Test
    void shutdown_should_swallow_close_exception() throws Exception {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        DynamicElasticsearchConfig cfg = new DynamicElasticsearchConfig(systemConfigurationService);

        RestClient client = mock(RestClient.class);
        doThrow(new RuntimeException("close boom")).when(client).close();
        @SuppressWarnings("unchecked")
        AtomicReference<RestClient> ref = (AtomicReference<RestClient>) ReflectionTestUtils.getField(cfg, "currentClient");
        ref.set(client);

        cfg.shutdown();
    }
}

