package com.example.EnterpriseRagCommunity.service.es;

import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticsearchIkAnalyzerProbeTest {

    @BeforeEach
    void setUp() {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
    }

    @Test
    void isIkSupported_normalizesEndpoint_andSendsAuthHeader_whenApiKeyPresent() {
        MockHttpUrl.enqueue(200, "{\"ok\":true}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es/,mockhttp://backup", "  key-1  ");

        boolean supported = probe.isIkSupported();

        assertTrue(supported);
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertEquals("POST", req.method());
        assertEquals("mockhttp://es/_analyze", req.url().toString());
        assertEquals("application/json", req.headers().get("Content-Type"));
        assertEquals("ApiKey key-1", req.headers().get("Authorization"));
        assertEquals("{\"analyzer\":\"ik_smart\",\"text\":\"test\"}", new String(req.body(), StandardCharsets.UTF_8));
    }

    @Test
    void isIkSupported_doesNotSendAuthHeader_whenApiKeyBlank() {
        MockHttpUrl.enqueue(200, "{\"ok\":true}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", "   ");

        boolean supported = probe.isIkSupported();

        assertTrue(supported);
        MockHttpUrl.RequestCapture req = MockHttpUrl.pollRequest();
        assertNotNull(req);
        assertNull(req.headers().get("Authorization"));
    }

    @Test
    void isIkSupported_returnsFalse_whenUnknownAnalyzerAppearsInErrorBody() {
        MockHttpUrl.enqueue(400, "{\"error\":\"unknown analyzer type [ik_smart]\"}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_returnsFalse_whenOnlyIkSmartKeywordAppearsInErrorBody() {
        MockHttpUrl.enqueue(400, "{\"error\":\"ik_smart plugin missing\"}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_returnsFalse_whenErrorBodyDoesNotContainUnknownAnalyzerKeywords() {
        MockHttpUrl.enqueue(500, "{\"error\":\"boom\"}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_returnsFalse_whenResponseCodeBelow200() {
        MockHttpUrl.enqueue(101, "{\"message\":\"switching protocols\"}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_returnsFalse_whenErrorStreamIsNull() {
        MockHttpUrl.enqueue(500, null);
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_returnsFalse_whenEndpointConfigIsNull() {
        ElasticsearchIkAnalyzerProbe probe = createProbe(null, null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_returnsFalse_whenEndpointConfigIsBlank() {
        ElasticsearchIkAnalyzerProbe probe = createProbe("   ", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    @Test
    void isIkSupported_usesCachedResult_onSecondCall() {
        MockHttpUrl.enqueue(400, "{\"error\":\"unknown analyzer\"}");
        MockHttpUrl.enqueue(200, "{\"ok\":true}");
        ElasticsearchIkAnalyzerProbe probe = createProbe("mockhttp://es", null);

        boolean first = probe.isIkSupported();
        boolean second = probe.isIkSupported();

        assertFalse(first);
        assertFalse(second);
        assertNotNull(MockHttpUrl.pollRequest());
        assertNull(MockHttpUrl.pollRequest());
    }

    @Test
    void isIkSupported_returnsFalse_whenUriBuildThrowsException() {
        ElasticsearchIkAnalyzerProbe probe = createProbe("http://::invalid-uri", null);

        boolean supported = probe.isIkSupported();

        assertFalse(supported);
    }

    private ElasticsearchIkAnalyzerProbe createProbe(String uris, String apiKey) {
        SystemConfigurationService systemConfigurationService = mock(SystemConfigurationService.class);
        when(systemConfigurationService.getConfig(eq("spring.elasticsearch.uris"))).thenReturn(uris);
        when(systemConfigurationService.getConfig(eq("APP_ES_API_KEY"))).thenReturn(apiKey);
        return new ElasticsearchIkAnalyzerProbe(systemConfigurationService);
    }
}
