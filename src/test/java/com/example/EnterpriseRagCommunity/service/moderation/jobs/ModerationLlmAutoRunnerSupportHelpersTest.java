package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModerationLlmAutoRunnerSupportHelpersTest {

    @Test
    void extractEntitiesFromText_should_deduplicate_urls_and_respect_limit() {
        List<Map<String, Object>> out = ModerationLlmAutoRunnerSupport.extractEntitiesFromText(
                "https://a.test https://a.test www.a.test 13800138000 wx:wechatid",
                7,
                3
        );

        assertEquals(3, out.size());
        assertEquals("URL", out.get(0).get("type"));
        assertEquals("https://a.test", out.get(0).get("value"));
        assertEquals("URL", out.get(1).get("type"));
        assertEquals("www.a.test", out.get(1).get("value"));
        assertEquals("PHONE", out.get(2).get("type"));
        assertEquals("13800138000", out.get(2).get("value"));
    }

    @Test
    void readChunkCollection_should_support_string_and_integer_keys() {
        Map<Object, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("1", List.of("a"));
        byChunk.put(2, List.of("b"));

        assertEquals(List.of("a"), ModerationLlmAutoRunnerSupport.readChunkCollection(byChunk, 1));
        assertEquals(List.of("b"), ModerationLlmAutoRunnerSupport.readChunkCollection(byChunk, 2));
        assertNull(ModerationLlmAutoRunnerSupport.readChunkCollection(byChunk, 3));
    }

    @Test
    void summarizeHelpers_should_reuse_common_fields_and_truncate_raw_output() {
        LlmModerationTestResponse.Stage stage = new LlmModerationTestResponse.Stage();
        stage.setDecisionSuggestion("REJECT");
        stage.setRiskScore(0.9);
        stage.setLabels(List.of("tag1"));
        stage.setReasons(List.of("r1", "r2"));
        stage.setEvidence(List.of("e1"));
        stage.setInputMode("text");
        stage.setModel("m1");
        stage.setLatencyMs(12L);
        stage.setDescription("desc");
        stage.setRawModelOutput("x".repeat(1200));

        Map<String, Object> stageSummary = ModerationLlmAutoRunnerSupport.summarizeLlmStage(stage);
        assertEquals("REJECT", stageSummary.get("decision_suggestion"));
        assertEquals("desc", stageSummary.get("description"));
        assertEquals(1000, String.valueOf(stageSummary.get("rawModelOutput")).length());

        LlmModerationTestResponse response = new LlmModerationTestResponse();
        response.setDecision("APPROVE");
        response.setRiskTags(List.of("safe"));
        response.setReasons(List.of("ok"));
        response.setEvidence(List.of("proof"));
        response.setInputMode("multistage");
        response.setModel("m2");
        response.setLatencyMs(34L);
        response.setRawModelOutput("y".repeat(1100));

        Map<String, Object> responseSummary = ModerationLlmAutoRunnerSupport.summarizeLlmRes(response);
        assertEquals("APPROVE", responseSummary.get("decision"));
        assertEquals(List.of("safe"), responseSummary.get("riskTags"));
        assertEquals(1000, String.valueOf(responseSummary.get("rawModelOutput")).length());
    }
}
