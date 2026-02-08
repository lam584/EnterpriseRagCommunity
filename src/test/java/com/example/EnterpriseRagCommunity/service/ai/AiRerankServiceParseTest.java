package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AiRerankServiceParseTest {

    @Test
    void parseResults_supportsNativeShape_outputResults() {
        AiRerankService svc = new AiRerankService(new AiProperties(), null, null);
        String raw = """
                {
                  "output": {
                    "results": [
                      {"index": 0, "relevance_score": 0.933},
                      {"index": 2, "relevance_score": 0.341}
                    ]
                  },
                  "usage": {"total_tokens": 79}
                }
                """;

        List<AiRerankService.RerankHit> hits = svc.parseResults(raw);
        assertEquals(2, hits.size());
        assertEquals(0, hits.get(0).index());
        assertEquals(0.933, hits.get(0).relevanceScore(), 1e-9);

        LlmCallQueueService.UsageMetrics usage = svc.parseUsageFromJson(raw);
        assertNotNull(usage);
        assertEquals(79, usage.totalTokens());
    }

    @Test
    void parseResults_supportsCompatShape_dataArray() {
        AiRerankService svc = new AiRerankService(new AiProperties(), null, null);
        String raw = """
                {
                  "data": [
                    {"index": 1, "relevance_score": 0.8},
                    {"index": 0, "relevance_score": 0.2}
                  ],
                  "usage": {"total_tokens": 10}
                }
                """;

        List<AiRerankService.RerankHit> hits = svc.parseResults(raw);
        assertEquals(2, hits.size());
        assertEquals(1, hits.get(0).index());
        assertEquals(0.8, hits.get(0).relevanceScore(), 1e-9);

        LlmCallQueueService.UsageMetrics usage = svc.parseUsageFromJson(raw);
        assertNotNull(usage);
        assertEquals(10, usage.totalTokens());
    }

    @Test
    void normalizeResponses_extractsOutputTextJson() throws Exception {
        AiRerankService svc = new AiRerankService(new AiProperties(), null, null);
        String raw = """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "{\\"results\\":[{\\"index\\":0,\\"relevance_score\\":0.77}]}"}
                      ]
                    }
                  ]
                }
                """;

        String normalized = svc.normalizeRerankJsonFromResponsesResponse(raw);
        List<AiRerankService.RerankHit> hits = svc.parseResults(normalized);
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).index());
        assertEquals(0.77, hits.get(0).relevanceScore(), 1e-9);
    }
}
