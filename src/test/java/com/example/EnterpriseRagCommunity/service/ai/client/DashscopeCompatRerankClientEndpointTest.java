package com.example.EnterpriseRagCommunity.service.ai.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashscopeCompatRerankClientEndpointTest {

    @Test
    void normalizeRootUrl_stripsV1Suffix() throws Exception {
        Method m = DashscopeCompatRerankClient.class.getDeclaredMethod("normalizeRootUrl", String.class, String.class);
        m.setAccessible(true);

        assertEquals("http://localhost:20768", m.invoke(null, "http://localhost:20768/v1", null));
        assertEquals("http://localhost:20768", m.invoke(null, "http://localhost:20768/api/v1", null));
        assertEquals("http://localhost:20768", m.invoke(null, "http://localhost:20768", null));
    }

    @Test
    void buildEndpoint_joinsRootAndCompatibleApiPath() throws Exception {
        Method m = DashscopeCompatRerankClient.class.getDeclaredMethod("buildEndpoint", String.class, String.class);
        m.setAccessible(true);
        assertEquals(
                "http://localhost:20768/compatible-api/v1/reranks",
                m.invoke(null, "http://localhost:20768", "/compatible-api/v1/reranks")
        );
    }
}
