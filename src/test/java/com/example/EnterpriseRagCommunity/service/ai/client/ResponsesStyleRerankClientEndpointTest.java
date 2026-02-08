package com.example.EnterpriseRagCommunity.service.ai.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResponsesStyleRerankClientEndpointTest {

    @Test
    void buildEndpoint_supportsAbsoluteV1PathWithoutDoubleV1() throws Exception {
        Method m = ResponsesStyleRerankClient.class.getDeclaredMethod("buildEndpoint", String.class, String.class);
        m.setAccessible(true);

        assertEquals(
                "http://localhost:20768/v1/rerank",
                m.invoke(null, "http://localhost:20768/v1", "/v1/rerank")
        );
        assertEquals(
                "http://localhost:20768/v1/rerank",
                m.invoke(null, "http://localhost:20768/v1", "/rerank")
        );
    }
}
