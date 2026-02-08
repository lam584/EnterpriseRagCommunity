package com.example.EnterpriseRagCommunity.service.ai.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashscopeRerankClientEndpointTest {

    @Test
    void selectEndpoint_qwen3Rerank_switchesToCompatibleApi() throws Exception {
        Method m = DashscopeRerankClient.class.getDeclaredMethod("selectEndpoint", String.class, String.class);
        m.setAccessible(true);
        String endpoint = (String) m.invoke(null, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3-rerank");
        assertEquals("https://dashscope.aliyuncs.com/compatible-api/v1/reranks", endpoint);
    }

    @Test
    void selectEndpoint_gteRerank_usesNativeRerankService() throws Exception {
        Method m = DashscopeRerankClient.class.getDeclaredMethod("selectEndpoint", String.class, String.class);
        m.setAccessible(true);
        String endpoint = (String) m.invoke(null, "https://dashscope.aliyuncs.com/compatible-mode/v1", "gte-rerank-v2");
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank", endpoint);
    }

    @Test
    void selectEndpoint_qwen3Rerank_worksForIntlHost() throws Exception {
        Method m = DashscopeRerankClient.class.getDeclaredMethod("selectEndpoint", String.class, String.class);
        m.setAccessible(true);
        String endpoint = (String) m.invoke(null, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen3-rerank");
        assertEquals("https://dashscope-intl.aliyuncs.com/compatible-api/v1/reranks", endpoint);
    }
}

