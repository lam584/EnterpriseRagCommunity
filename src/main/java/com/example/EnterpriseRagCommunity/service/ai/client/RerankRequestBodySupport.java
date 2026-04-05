package com.example.EnterpriseRagCommunity.service.ai.client;

import java.util.List;
import java.util.Map;

final class RerankRequestBodySupport {

    private RerankRequestBodySupport() {
    }

    static Map<String, Object> buildBaseBody(String model, String query, List<String> documents, Integer topN) {
        Map<String, Object> body = RerankUrlSupport.buildBaseRequestBody(model, query, documents);
        if (topN != null && topN > 0) body.put("top_n", topN);
        return body;
    }
}
