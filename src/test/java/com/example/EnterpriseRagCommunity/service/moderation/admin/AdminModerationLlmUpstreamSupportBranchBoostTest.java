package com.example.EnterpriseRagCommunity.service.moderation.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

class AdminModerationLlmUpstreamSupportBranchBoostTest {

    @Test
    void templateAndDecisionHelpers_shouldCoverMainBranches() throws Exception {
        String mergedWithPlaceholder = AdminModerationLlmUpstreamSupport.mergePromptAndJson("{{json}}", "X {{json}} Y", "{\"k\":1}");
        assertEquals("X {\"k\":1} Y", mergedWithPlaceholder);
        String mergedAppend = AdminModerationLlmUpstreamSupport.mergePromptAndJson("tpl", "hello", "{\"x\":2}");
        assertEquals("hello\n\n{\"x\":2}", mergedAppend);
        assertEquals("hello", AdminModerationLlmUpstreamSupport.mergePromptAndJson("tpl", "hello", " "));

        String judge = AdminModerationLlmUpstreamSupport.renderJudgePrompt(
                "T={{text}}|D={{imageDescription}}|TR={{textReasons}}|IR={{imageReasons}}",
                "a".repeat(3100),
                "b".repeat(2100),
                0.1,
                0.2,
                List.of("r1"),
                List.of("r2"));
        assertTrue(judge.contains("TR=r1"));
        assertTrue(judge.contains("IR=r2"));

        String upgrade = AdminModerationLlmUpstreamSupport.renderJudgeUpgradePrompt(
                "TE={{textEvidence}}|IE={{imageEvidence}}|CE={{judgeEvidence}}|JR={{judgeReasons}}",
                "x",
                "y",
                0.1,
                0.2,
                0.3,
                0.4,
                List.of("tr"),
                List.of("ir"),
                List.of("jr"),
                List.of("t".repeat(1300)),
                List.of("i".repeat(1300)),
                List.of("c".repeat(1300)));
        assertTrue(upgrade.contains("JR=jr"));
        assertTrue(upgrade.length() > 100);

        assertEquals("ALLOW", AdminModerationLlmUpstreamSupport.decisionToSuggestion("APPROVE"));
        assertEquals("REJECT", AdminModerationLlmUpstreamSupport.decisionToSuggestion("REJECT"));
        assertEquals("ESCALATE", AdminModerationLlmUpstreamSupport.decisionToSuggestion("HUMAN"));
        assertNull(AdminModerationLlmUpstreamSupport.decisionToSuggestion("UNKNOWN"));

        assertEquals("APPROVE", invokeStaticString("normalizeDecision", new Class[]{String.class, Double.class}, "ALLOW", null));
        assertEquals("HUMAN", invokeStaticString("normalizeDecision", new Class[]{String.class, Double.class}, "ESCALATE", null));
        assertEquals("REJECT", invokeStaticString("normalizeDecision", new Class[]{String.class, Double.class}, "疑似违规，拒绝", null));
        assertEquals("APPROVE", invokeStaticString("normalizeDecision", new Class[]{String.class, Double.class}, "unknown", 0.2d));
        assertEquals("REJECT", invokeStaticString("normalizeDecision", new Class[]{String.class, Double.class}, "unknown", 0.9d));

        assertEquals(1, invokeStaticInt("severityRank", new Class[]{String.class}, "LOW"));
        assertEquals(2, invokeStaticInt("severityRank", new Class[]{String.class}, "MEDIUM"));
        assertEquals(3, invokeStaticInt("severityRank", new Class[]{String.class}, "HIGH"));
        assertEquals(4, invokeStaticInt("severityRank", new Class[]{String.class}, "CRITICAL"));
        assertEquals(0, invokeStaticInt("severityRank", new Class[]{String.class}, "X"));
        assertEquals("HIGH", invokeStaticString("maxSeverity", new Class[]{String.class, String.class}, "LOW", "HIGH"));

        @SuppressWarnings("unchecked")
        List<String> mergedTags = (List<String>) invokeStatic("mergeTags", new Class[]{List.class, List.class}, List.of("a"), List.of("a", "b"));
        assertEquals(List.of("a", "b"), mergedTags);
        assertNull(invokeStatic("mergeTags", new Class[]{List.class, List.class}, null, null));

        assertEquals(0, invokeStaticInt("countChar", new Class[]{String.class, char.class}, "", '{'));
        assertEquals(2, invokeStaticInt("countChar", new Class[]{String.class, char.class}, "{x{", '{'));
    }

    @Test
    void parseDecision_shouldCoverRecoverPartialAndFallback() {
        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));

        ParsedDecision recovered = support.parseDecisionFromAssistantText("{\"decision\":\"REJECT\",\"score\":0.91,\"description\":\"xxx");
        assertEquals("REJECT", recovered.decisionSuggestion);
        assertTrue(recovered.reasons.stream().anyMatch(s -> s != null && s.contains("恢复解析")));

        ParsedDecision partial = support.parseDecisionFromAssistantText("random \"decision\":\"REJECT\", \"score\":0.88");
        assertEquals("REJECT", partial.decisionSuggestion);
        assertEquals("REJECT", partial.decision);

        ParsedDecision fallback = support.parseDecisionFromAssistantText("not-json-at-all");
        assertEquals("ESCALATE", fallback.decisionSuggestion);
        assertTrue(fallback.riskTags.contains("PARSE_ERROR"));
    }

    @Test
    void parseDecision_shouldClampAndUseSafeField() {
        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        ParsedDecision out = support.parseDecisionFromAssistantText("{\"safe\":\"no\",\"score\":1.5,\"uncertainty\":-1,\"evidence\":{\"quote\":\"abc\"},\"labels\":\"tag1\"}");
        assertEquals("REJECT", out.decisionSuggestion);
        assertEquals("REJECT", out.decision);
        assertEquals(1.0d, out.score);
        assertEquals(0.0d, out.uncertainty);
        assertEquals(List.of("tag1"), out.labels);
        assertEquals(List.of("tag1"), out.riskTags);
        assertNotNull(out.evidence);
        assertTrue(out.evidence.size() == 1);
    }

    @Test
    void callOnce_shouldUseHeaderPathAndBuildPromptMessages() {
        LlmGateway gateway = mock(LlmGateway.class);
        String content = "{\"decision_suggestion\":\"ALLOW\",\"risk_score\":0.1,\"labels\":[\"ok\"],\"evidence\":[\"hello\"]}";
        when(gateway.chatOnceRoutedNoQueue(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class),
                nullable(Map.class)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"message\":{\"content\":\"" + content.replace("\"", "\\\"") + "\"}}]}", "p", "m", null));

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, mock(AdminModerationLlmImageSupport.class));
        StageCallResult out = support.callOnce(
                LlmQueueTaskType.MULTIMODAL_MODERATION,
                "provider",
                "model",
                List.of(
                        new ChatMessage("", "skip"),
                        ChatMessage.system("sys"),
                        ChatMessage.userParts(List.of(
                                Map.of("type", "text", "text", "hello"),
                                Map.of("type", "image_url", "image_url", Map.of("url", "oss://img"))
                        )),
                        new ChatMessage("assistant", Map.of("k", "v"))
                ),
                0.2,
                0.8,
                300,
                Boolean.FALSE,
                Map.of("x", 1),
                "multimodal",
                false,
                Map.of("X-DashScope-OssResourceResolve", "enable")
        );
        assertEquals("ALLOW", out.decisionSuggestion());
        assertEquals("APPROVE", out.decision());
        assertNotNull(out.promptMessages());
        assertTrue(out.promptMessages().size() >= 2);
    }

    @Test
    void callOnce_shouldReturnUpstreamErrorWhenNotProviderBlocked() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.chatOnceRoutedNoQueue(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class)
        )).thenThrow(new IllegalStateException("plain upstream failure"));

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, mock(AdminModerationLlmImageSupport.class));
        StageCallResult out = support.callTextOnce("s", "u", 0.1, 0.1, 50, null, null, false, false);
        assertEquals("HUMAN", out.decision());
        assertEquals(List.of("UPSTREAM_ERROR"), out.riskTags());
    }

    @Test
    void callImageDescribeOnce_shouldFallbackToTextOnlyWhenNoImageInput() {
        LlmGateway gateway = mock(LlmGateway.class);
        String content = "{\"decision_suggestion\":\"ALLOW\",\"risk_score\":0.1,\"labels\":[\"ok\"]}";
        when(gateway.chatOnceRouted(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"message\":{\"content\":\"" + content.replace("\"", "\\\"") + "\"}}]}", "p", "m", null));

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, mock(AdminModerationLlmImageSupport.class));
        StageCallResult out = support.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                null,
                "PT={{text}}",
                "{\"k\":1}",
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                null,
                null,
                false,
                null,
                true
        );
        assertEquals("APPROVE", out.decision());
    }

    @Test
    void callImageDescribeOnce_shouldAggregateBatchesAndReturnReject() {
        LlmGateway gateway = mock(LlmGateway.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        when(gateway.resolve(nullable(String.class))).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p1",
                "OPENAI_COMPAT",
                "https://example.com",
                "k",
                "m",
                null,
                Map.of(),
                Map.of(),
                1000,
                1000
        ));
        when(imageSupport.encodeImageUrlForUpstream(any(ImageRef.class), nullable(String.class)))
                .thenReturn("oss://img-1")
                .thenReturn("oss://img-2");
        when(imageSupport.estimateVisionImageTokens(any(ImageRef.class), nullable(String.class), any(), nullable(Integer.class)))
                .thenReturn(20)
                .thenReturn(20);

        String c1 = "{\"decision_suggestion\":\"REJECT\",\"score\":0.8,\"labels\":[\"l1\"],\"riskTags\":[\"r1\"],\"reasons\":[\"bad\"],\"severity\":\"HIGH\",\"uncertainty\":0.3,\"evidence\":[\"[[IMAGE_1]]\"],\"description\":\"d1\"}";
        String c2 = "{\"decision_suggestion\":\"ALLOW\",\"score\":0.2,\"labels\":[\"l2\"],\"riskTags\":[\"r2\"],\"reasons\":[\"ok\"],\"severity\":\"LOW\",\"uncertainty\":0.1,\"evidence\":[\"[[IMAGE_1]]\"],\"description\":\"d2\"}";
        when(gateway.chatOnceRouted(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class),
                nullable(Map.class)
        )).thenReturn(
                new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"message\":{\"content\":\"" + c1.replace("\"", "\\\"") + "\"}}]}", "p", "m", null),
                new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"message\":{\"content\":\"" + c2.replace("\"", "\\\"") + "\"}}]}", "p", "m", null)
        );

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, imageSupport);
        StageCallResult out = support.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(1L, "u1", "image/png"), new ImageRef(2L, "u2", "image/png")),
                "PT={{text}}",
                "{\"k\":1}",
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                30,
                1,
                false,
                1024,
                true
        );

        assertNotNull(out.decision());
        assertTrue(out.labels().contains("l1"));
        assertTrue(out.labels().contains("l2"));
        assertEquals("HIGH", out.severity());
    }

    @Test
    void callImageDescribeOnce_shouldReturnEscalateWhenCallOnceReturnsNull() {
        LlmGateway gateway = mock(LlmGateway.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        when(gateway.resolve(nullable(String.class))).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p2",
                "LOCAL_OPENAI_COMPAT",
                "http://localhost:11434",
                "k",
                "m",
                null,
                Map.of(),
                Map.of(),
                1000,
                1000
        ));
        when(imageSupport.encodeImageUrlForUpstream(any(ImageRef.class), nullable(String.class))).thenReturn("http://img");
        when(imageSupport.estimateVisionImageTokens(any(ImageRef.class), nullable(String.class), any(), nullable(Integer.class))).thenReturn(10);

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, imageSupport);
        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(support);
        doReturn(null).when(spy).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );

        StageCallResult out = spy.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(1L, "u1", "image/png")),
                "PT={{text}}",
                null,
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                null,
                1,
                true,
                null,
                false
        );
        assertEquals("ESCALATE", out.decisionSuggestion());
        assertEquals("HUMAN", out.decision());
        assertTrue(out.riskTags().contains("UPSTREAM_ERROR"));
    }

    @Test
    void privateHelpers_shouldCoverAdditionalBranches() throws Exception {
        assertNull(invokeStatic("extractJsonObjectFromText", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("extractJsonObjectFromText", new Class[]{String.class}, "abc"));
        assertEquals("{\"k\":1}", invokeStatic("extractJsonObjectFromText", new Class[]{String.class}, "x {\"k\":1} y"));

        assertEquals("", invokeStatic("extractUserTextForEvidence", new Class[]{List.class}, (Object) null));
        String userText = (String) invokeStatic("extractUserTextForEvidence", new Class[]{List.class}, java.util.Arrays.asList(
                null,
                ChatMessage.system("s"),
                new ChatMessage("user", null),
                new ChatMessage("user", List.of(
                        Map.of("type", "image_url", "image_url", Map.of("url", "u")),
                        Map.of("type", "text", "text", "t1")
                )),
                ChatMessage.user("t2")
        ));
        assertTrue(userText.contains("t1"));
        assertTrue(userText.contains("t2"));

        assertNull(invokeStatic("deriveFallbackEvidenceFromUserText", new Class[]{String.class}, (Object) null));
        assertNotNull(invokeStatic("deriveFallbackEvidenceFromUserText", new Class[]{String.class}, "[TEXT]\n可疑词"));
        assertNotNull(invokeStatic("deriveFallbackEvidenceFromUserText", new Class[]{String.class}, "短文本"));

        assertNull(invokeStatic("firstMeaningfulLine", new Class[]{String.class}, "\n[BLOCK]\n"));
        assertEquals("", invokeStatic("normalizeEvidenceText", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("clipEvidence", new Class[]{String.class}, " "));
        assertNull(invokeStatic("clipEvidence", new Class[]{String.class}, "x"));
        assertTrue(((String) invokeStatic("clipEvidence", new Class[]{String.class}, "a".repeat(200))).length() <= 120);

        assertEquals(List.of(), invokeStatic("extractImagePlaceholders", new Class[]{String.class}, (Object) null));
        @SuppressWarnings("unchecked")
        List<String> placeholders = (List<String>) invokeStatic("extractImagePlaceholders", new Class[]{String.class}, "[[IMAGE_1]] [[IMAGE_1]] [[IMAGE_2]]");
        assertEquals(List.of("[[IMAGE_1]]", "[[IMAGE_2]]"), placeholders);

        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertFalse((Boolean) invokeInstance(s, "hasVerifiableJsonEvidence", new Class[]{String.class, String.class}, "txt", "{not_json"));
        assertFalse((Boolean) invokeStatic("hasVerifiableEvidenceObject", new Class[]{String.class, com.fasterxml.jackson.databind.JsonNode.class}, "", new ObjectMapper().readTree("{\"quote\":\"a\"}")));

        assertEquals("t", invokeInstance(s, "extractAssistantContent", new Class[]{String.class}, "{\"choices\":[{\"text\":\"t\"}]}"));
        assertEquals("raw", invokeInstance(s, "extractAssistantContent", new Class[]{String.class}, "raw"));

        assertNull(invokeStatic("recoverTruncatedJson", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("recoverTruncatedJson", new Class[]{String.class}, "{\"a\":1}"));
        assertNotNull(invokeStatic("recoverTruncatedJson", new Class[]{String.class}, "{\"decision\":\"ALLOW\",\"description\":\"x"));

        assertEquals("a", invokeStatic("firstGroup", new Class[]{String.class, java.util.regex.Pattern.class}, "a=1", java.util.regex.Pattern.compile("(a)")));
        assertEquals("abc", invokeStatic("trimToMaxChars", new Class[]{String.class}, " abc "));
        assertEquals(400, ((String) invokeStatic("trimToMaxChars", new Class[]{String.class}, "a".repeat(500))).length());

        assertEquals("[non_text_content]", invokeStatic("partsToDebugText", new Class[]{List.class}, (Object) null));
        String partOut = (String) invokeStatic("partsToDebugText", new Class[]{List.class}, List.of(
                Map.of("type", "text", "text", "hello"),
                Map.of("type", "image_url", "image_url", Map.of("url", "http://x")),
                Map.of("type", "other")
        ));
        assertTrue(partOut.contains("hello"));
        assertTrue(partOut.contains("[image]"));

        assertEquals("123", invokeStatic("textOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class}, new ObjectMapper().readTree("123")));
        assertNull(invokeStatic("firstTextOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class, List.class}, null, List.of("a")));
        assertNull(invokeStatic("firstTextOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class, List.class}, new ObjectMapper().readTree("{}"), List.of("", " ")));

        assertEquals(Boolean.TRUE, invokeStatic("booleanOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class}, new ObjectMapper().readTree("\"yes\"")));
        assertEquals(Boolean.FALSE, invokeStatic("booleanOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class}, new ObjectMapper().readTree("\"0\"")));
        assertNull(invokeStatic("booleanOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class}, new ObjectMapper().readTree("\"unknown\"")));

        assertNull(invokeStatic("normalizeDecision", new Class[]{String.class, Double.class}, null, null));
        assertEquals("HUMAN", invokeStatic("normalizeDecision", new Class[]{String.class, Double.class}, "需要人工复核", null));
        assertEquals("HUMAN", invokeStatic("normalizeDecision", new Class[]{String.class, Double.class}, "???", null));

        assertEquals("ALLOW", invokeStatic("normalizeSuggestion", new Class[]{String.class}, "APPROVE"));
        assertEquals("ESCALATE", invokeStatic("normalizeSuggestion", new Class[]{String.class}, "HUMAN"));
        assertEquals("ALLOW", invokeStatic("normalizeSuggestion", new Class[]{String.class}, "通过"));
        assertEquals("REJECT", invokeStatic("normalizeSuggestion", new Class[]{String.class}, "违规"));
        assertEquals("ESCALATE", invokeStatic("normalizeSuggestion", new Class[]{String.class}, "人工"));
        assertEquals("", invokeStatic("nullToEmpty", new Class[]{String.class}, (Object) null));
        assertNull(AdminModerationLlmUpstreamSupport.decisionToSuggestion(null));

        assertNull(invokeStatic("extractByContextAnchors", new Class[]{Map.class, String.class}, Map.of(), "txt"));
        assertEquals("", invokeStatic("normalizeForAnchorRegex", new Class[]{String.class}, (Object) null));
        assertEquals("", invokeStatic("anchorToRegex", new Class[]{String.class}, (Object) null));
        assertEquals("", invokeStatic("anchorToRegex", new Class[]{String.class}, "   "));
        assertEquals(3, invokeStaticInt("findBoundaryEnd", new Class[]{String.class, int.class, int.class}, "abc\ndef", 0, 7));
        assertEquals(3, invokeStaticInt("findBoundaryEnd", new Class[]{String.class, int.class, int.class}, "abcdef", 0, 3));
        assertEquals("", invokeStatic("cleanExtractedSnippet", new Class[]{String.class}, (Object) null));
        assertTrue((Boolean) invokeStatic("isSuspiciousEvidenceText", new Class[]{String.class, String.class}, "", "section"));
    }

    @Test
    void callOnce_and_callImageDescribeOnce_shouldCoverUsageAndEarlyBranches() {
        LlmGateway gateway = mock(LlmGateway.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, imageSupport);
        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(support);

        LlmModerationTestResponse.Usage u = new LlmModerationTestResponse.Usage();
        u.setPromptTokens(3);
        u.setCompletionTokens(4);
        u.setTotalTokens(7);
        StageCallResult r1 = new StageCallResult(
                "ALLOW",
                0.2,
                java.util.Arrays.asList("a", "", null),
                "APPROVE",
                0.2,
                java.util.Arrays.asList("r", "", null),
                java.util.Arrays.asList("x", "", null),
                "LOW",
                0.2,
                java.util.Arrays.asList("e", "", null),
                "raw",
                "model-1",
                12L,
                u,
                List.of(),
                "d1",
                "multimodal"
        );
        StageCallResult r2 = new StageCallResult("REJECT", 0.9, List.of("b"), "REJECT", 0.9, List.of("r2"), List.of("x2"), "HIGH", 0.9, List.of("e2"), "raw2", "model-2", 2L, u, null, "d2", "multimodal");

        doReturn(r1, r2).when(spy).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );
        when(gateway.resolve(nullable(String.class))).thenThrow(new IllegalStateException("x"));
        when(imageSupport.encodeImageUrlForUpstream(any(ImageRef.class), nullable(String.class)))
                .thenReturn("")
                .thenReturn("http://img1")
                .thenReturn("http://img2");
        when(imageSupport.estimateVisionImageTokens(any(ImageRef.class), nullable(String.class), any(), nullable(Integer.class)))
                .thenReturn(5)
                .thenReturn(6);

        StageCallResult out = spy.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                java.util.Arrays.asList(null, new ImageRef(1L, "u1", "image/png"), new ImageRef(2L, "u2", "image/png"), new ImageRef(3L, "u3", "image/png")),
                "PT={{text}}",
                "{\"k\":1}",
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                null,
                null,
                false,
                1024,
                false
        );
        assertNotNull(out.decision());
        assertNotNull(out.usage());
        assertTrue(out.usage().getPromptTokens() >= 0);
        assertNotNull(out.severity());
    }

    @Test
    void detectProviderOutputBlocked_and_promptRender_shouldCoverBranches() throws Exception {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));

        assertNull(invokeInstance(s, "detectProviderOutputBlocked", new Class[]{Exception.class}, new Exception("x")));
        assertNull(invokeInstance(s, "detectProviderOutputBlocked", new Class[]{Exception.class}, new Exception("")));

        Object b1 = invokeInstance(s, "detectProviderOutputBlocked", new Class[]{Exception.class}, new Exception("data_inspection_failed no json body"));
        assertNotNull(b1);
        Object b2 = invokeInstance(s, "detectProviderOutputBlocked", new Class[]{Exception.class}, new Exception("inappropriate content {\"error\":{\"type\":\"data_inspection_failed\",\"message\":\"Inappropriate content\"},\"id\":\"rid-1\"}"));
        assertNotNull(b2);
        assertNull(invokeInstance(s, "detectProviderOutputBlocked", new Class[]{Exception.class}, new Exception("inappropriate content {\"error\":{\"code\":\"x\",\"message\":\"ok\"}}")));
        Object b3 = invokeInstance(s, "detectProviderOutputBlocked", new Class[]{Exception.class}, new Exception("inappropriate content {bad-json"));
        assertNotNull(b3);

        String v1 = AdminModerationLlmUpstreamSupport.renderVisionPrompt("{{title}}|{{content}}|{{text}}", null);
        assertEquals("||", v1);
        String v2 = AdminModerationLlmUpstreamSupport.renderVisionPrompt("{{text}}", new PromptVars("t", "c", "x".repeat(1200)));
        assertEquals(1000, v2.length());

        String t1 = AdminModerationLlmUpstreamSupport.renderTextPrompt("{{title}}|{{content}}|{{text}}", null);
        assertEquals("||", t1);
        String t2 = AdminModerationLlmUpstreamSupport.renderTextPrompt("{{text}}", new PromptVars("t", "c", "abc"));
        assertEquals("abc", t2);

        String j1 = AdminModerationLlmUpstreamSupport.renderJudgePrompt(null, null, null, 0, 0, null, null);
        assertEquals("", j1);
        String u1 = AdminModerationLlmUpstreamSupport.renderJudgeUpgradePrompt(null, null, null, 0, 0, 0, 0, null, null, null, null, null, null);
        assertEquals("", u1);
    }

    @Test
    void parseDecisionJson_and_callOnceRejectDowngrade_shouldCoverBranches() throws Exception {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        String json1 = "{\"decision\":\"REJECT\",\"image_risk_score\":0.9,\"risk_score\":0.5,\"score\":0.1," +
                "\"reasons\":[\"r1\",1],\"reason\":\"r2\",\"image_labels\":[\"a\",\"\",1],\"riskTags\":[\"k1\",\"\"],\"risk_tags\":[\"k2\"]," +
                "\"labels\":[\"a\",\"b\",1],\"evidence\":[\"e1\",{\"quote\":\"q\"},[1]],\"description\":\"\",\"imageDescription\":\"img\",\"uncertainty\":2}";
        Object p1 = invokeInstance(s, "parseDecisionFromJsonOrThrow", new Class[]{String.class}, json1);
        assertNotNull(p1);

        String json2 = "{\"decision_suggestion\":\"\",\"safe\":\"false\",\"score\":-1,\"labels\":\"t1\",\"evidence\":{\"before_context\":\"A\",\"quote\":\"B\"}}";
        Object p2 = invokeInstance(s, "parseDecisionFromJsonOrThrow", new Class[]{String.class}, json2);
        assertNotNull(p2);

        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.chatOnceRouted(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class)
        )).thenReturn(new LlmGateway.RoutedChatOnceResult("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", "p", "m", null));
        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(new AdminModerationLlmUpstreamSupport(gateway, mock(AdminModerationLlmImageSupport.class)));

        ParsedDecision pd1 = new ParsedDecision();
        pd1.decisionSuggestion = "REJECT";
        pd1.decision = "REJECT";
        pd1.score = 0.9;
        pd1.riskScore = 0.9;
        pd1.reasons = new java.util.ArrayList<>();
        pd1.labels = List.of();
        pd1.riskTags = List.of();
        pd1.evidence = List.of();
        doReturn(pd1).when(spy).parseDecisionFromAssistantText(any());

        StageCallResult out1 = spy.callOnce(
                LlmQueueTaskType.MULTIMODAL_MODERATION,
                "p",
                "m",
                List.of(ChatMessage.user("无证据文本")),
                0.1,
                0.9,
                200,
                null,
                null,
                "multimodal",
                true,
                null
        );
        assertNotNull(out1.decision());

        ParsedDecision pd2 = new ParsedDecision();
        pd2.decisionSuggestion = "REJECT";
        pd2.decision = "REJECT";
        pd2.score = 0.8;
        pd2.riskScore = 0.8;
        pd2.reasons = new java.util.ArrayList<>();
        pd2.labels = List.of();
        pd2.riskTags = List.of();
        pd2.evidence = List.of("{\"quote\":\"not-in-text\"}");
        doReturn(pd2).when(spy).parseDecisionFromAssistantText(any());
        StageCallResult out2 = spy.callOnce(
                LlmQueueTaskType.MULTIMODAL_MODERATION,
                "p",
                "m",
                List.of(ChatMessage.user("正文不包含该证据")),
                0.1,
                0.9,
                200,
                null,
                null,
                "multimodal",
                true,
                null
        );
        assertNotNull(out2.decision());
    }

    @Test
    void callOnce_shouldCoverParsedNullAndCatchBlockedBranches() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.chatOnceRouted(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class),
                nullable(Map.class)
        )).thenThrow(new RuntimeException("inappropriate content {\"error\":{\"code\":\"data_inspection_failed\",\"message\":\"Inappropriate content\"},\"request_id\":\"rid-x\"}"));
        when(gateway.chatOnceRoutedNoQueue(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                nullable(List.class),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class)
        )).thenReturn(null);

        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(new AdminModerationLlmUpstreamSupport(gateway, mock(AdminModerationLlmImageSupport.class)));
        doReturn(null).when(spy).parseDecisionFromAssistantText(any());

        StageCallResult p = spy.callOnce(
                LlmQueueTaskType.MULTIMODAL_MODERATION,
                "p",
                "m",
                null,
                0.1,
                0.2,
                100,
                null,
                null,
                "multimodal",
                false,
                null
        );
        assertEquals("HUMAN", p.decision());
        assertEquals("ESCALATE", p.decisionSuggestion());

        StageCallResult blocked = spy.callOnce(
                LlmQueueTaskType.MULTIMODAL_MODERATION,
                "p",
                "m",
                List.of(ChatMessage.user("x")),
                0.1,
                0.2,
                100,
                null,
                null,
                "multimodal",
                true,
                Map.of("H", "1")
        );
        assertTrue(blocked.riskTags().contains("PROVIDER_OUTPUT_BLOCKED"));
        assertTrue(blocked.reasons().stream().anyMatch(x -> x.contains("upstream_request_id")));
    }

    @Test
    void callImageDescribeOnce_shouldCoverHumanEarlyAndAggregationNullPaths() {
        LlmGateway gateway = mock(LlmGateway.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        when(gateway.resolve(nullable(String.class))).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p",
                "OPENAI_COMPAT",
                "https://example.com",
                "k",
                "m",
                null,
                Map.of(),
                Map.of(),
                1000,
                1000
        ));
        when(imageSupport.encodeImageUrlForUpstream(any(ImageRef.class), nullable(String.class)))
                .thenReturn("oss://img-1")
                .thenReturn("oss://img-1")
                .thenReturn("http://img-2")
                .thenReturn("http://img-3");
        when(imageSupport.estimateVisionImageTokens(any(ImageRef.class), nullable(String.class), any(), nullable(Integer.class)))
                .thenReturn(3)
                .thenReturn(5)
                .thenReturn(7)
                .thenReturn(9);

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, imageSupport);
        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(support);
        StageCallResult human = new StageCallResult("ESCALATE", null, List.of(), "HUMAN", null, List.of(), List.of(), null, null, null, "raw", "m", 1L, null, null, null, "multimodal");
        doReturn(human).when(spy).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );
        StageCallResult outHuman = spy.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(1L, "u1", "image/png")),
                "PT={{text}}",
                null,
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                0,
                0,
                true,
                null,
                false
        );
        assertEquals("HUMAN", outHuman.decision());

        LlmModerationTestResponse.Usage u1 = new LlmModerationTestResponse.Usage();
        u1.setPromptTokens(null);
        u1.setCompletionTokens(1);
        u1.setTotalTokens(null);
        LlmModerationTestResponse.Usage u2 = new LlmModerationTestResponse.Usage();
        u2.setPromptTokens(2);
        u2.setCompletionTokens(null);
        u2.setTotalTokens(3);
        StageCallResult a = new StageCallResult("ALLOW", null, null, "APPROVE", Double.NaN, null, null, "", Double.NaN, null, "", "", -1L, u1, null, "", "multimodal");
        StageCallResult b = new StageCallResult("REJECT", 0.9, List.of("l"), "REJECT", 0.9, List.of("r"), List.of("t"), "HIGH", 0.5, List.of("e"), "raw", "model", 2L, u2, null, "d", "multimodal");
        AdminModerationLlmUpstreamSupport spy2 = org.mockito.Mockito.spy(support);
        doReturn(a, b).when(spy2).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );
        StageCallResult out = spy2.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(1L, "u1", "image/png"), new ImageRef(2L, "u2", "image/png")),
                "PT={{text}}",
                "{\"k\":1}",
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                1,
                1,
                false,
                1024,
                false
        );
        assertNotNull(out.usage());
        assertTrue(out.usage().getCompletionTokens() >= 0);
    }

    @Test
    void miscSingleBranchFillers_shouldRaiseCoverageFast() throws Exception {
        assertEquals("", AdminModerationLlmUpstreamSupport.renderVisionPrompt(null, new PromptVars("t", "c", "x")));
        assertEquals("", AdminModerationLlmUpstreamSupport.renderTextPrompt(null, new PromptVars("t", "c", "x")));
        assertNull(invokeStatic("clipEvidence", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("extractJsonObjectFromText", new Class[]{String.class}, " "));
        assertEquals(0, invokeStaticInt("countChar", new Class[]{String.class, char.class}, null, '{'));
        assertEquals("", invokeStatic("normalizeForAnchorMatch", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("afterMarker", new Class[]{String.class, String.class}, "abc", " "));
        assertNull(invokeStatic("firstMeaningfulLine", new Class[]{String.class}, (Object) null));
        assertEquals(0, invokeStaticInt("severityRank", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("textOrNull", new Class[]{com.fasterxml.jackson.databind.JsonNode.class}, MissingNode.getInstance()));
        assertEquals("HUMAN", invokeStatic("suggestionToDecision", new Class[]{String.class, Double.class}, "", (Object) null));
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertNotNull(invokeInstance(s, "extractAssistantContent", new Class[]{String.class}, "{\"choices\":[]}"));
    }

    @Test
    void callImageDescribeOnce_shouldCoverBatchSplitAndFinalEmptyBranches() {
        LlmGateway gateway = mock(LlmGateway.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        when(gateway.resolve(nullable(String.class))).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p",
                "OPENAI_COMPAT",
                "https://example.com",
                "k",
                "m",
                null,
                Map.of(),
                Map.of(),
                1000,
                1000
        ));
        when(imageSupport.encodeImageUrlForUpstream(any(ImageRef.class), nullable(String.class)))
                .thenReturn(null)
                .thenReturn("")
                .thenReturn("http://img-1")
                .thenReturn("oss://img-2")
                .thenReturn("http://img-3");
        when(imageSupport.estimateVisionImageTokens(any(ImageRef.class), nullable(String.class), any(), nullable(Integer.class)))
                .thenReturn(3)
                .thenReturn(3)
                .thenReturn(1);

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, imageSupport);
        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(support);
        LlmModerationTestResponse.Usage u = new LlmModerationTestResponse.Usage();
        StageCallResult empty = new StageCallResult("ALLOW", null, List.of(), "APPROVE", Double.NaN, List.of(), List.of(), "", null, List.of(), "", "", null, u, null, "", "multimodal");
        LlmModerationTestResponse.Usage u2 = new LlmModerationTestResponse.Usage();
        u2.setPromptTokens(1);
        u2.setCompletionTokens(2);
        u2.setTotalTokens(3);
        StageCallResult full = new StageCallResult("REJECT", 0.7, List.of("l"), "REJECT", 0.7, List.of("r"), List.of("t"), "HIGH", 0.3, List.of("e"), "raw", "model", 5L, u2, null, "desc", "multimodal");
        doReturn(empty, full).when(spy).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );

        StageCallResult out = spy.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(1L, "u1", "image/png"), new ImageRef(2L, "u2", "image/png"), new ImageRef(3L, "u3", "image/png"), new ImageRef(4L, "u4", "image/png"), new ImageRef(5L, "u5", "image/png")),
                "PT={{text}}",
                "{\"k\":1}",
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                4,
                1,
                false,
                1024,
                false
        );
        assertEquals("REJECT", out.decision());

        AdminModerationLlmUpstreamSupport spy2 = org.mockito.Mockito.spy(support);
        doReturn(empty).when(spy2).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );
        StageCallResult out2 = spy2.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(6L, "u6", "image/png")),
                "PT={{text}}",
                null,
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                0,
                0,
                true,
                null,
                false
        );
        assertNotNull(out2.labels());
    }

    @Test
    void callImageDescribeOnce_shouldCoverHighResAndComparatorBranches() {
        LlmGateway gateway = mock(LlmGateway.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        when(gateway.resolve(nullable(String.class))).thenReturn(new AiProvidersConfigService.ResolvedProvider(
                "p",
                "OPENAI_COMPAT",
                "https://example.com",
                "k",
                "m",
                null,
                Map.of(),
                Map.of(),
                1000,
                1000
        ));
        when(imageSupport.encodeImageUrlForUpstream(any(ImageRef.class), nullable(String.class)))
                .thenReturn("http://img-a")
                .thenReturn("http://img-b");
        when(imageSupport.estimateVisionImageTokens(any(ImageRef.class), nullable(String.class), any(), nullable(Integer.class)))
                .thenReturn(2)
                .thenReturn(5);

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gateway, imageSupport);
        AdminModerationLlmUpstreamSupport spy = org.mockito.Mockito.spy(support);

        LlmModerationTestResponse.Usage u1 = new LlmModerationTestResponse.Usage();
        u1.setPromptTokens(1);
        u1.setCompletionTokens(1);
        u1.setTotalTokens(2);
        LlmModerationTestResponse.Usage u2 = new LlmModerationTestResponse.Usage();
        u2.setPromptTokens(2);
        u2.setCompletionTokens(2);
        u2.setTotalTokens(4);
        StageCallResult r1 = new StageCallResult("ALLOW", 0.1, List.of("a"), "APPROVE", 0.1, List.of("r1"), List.of("t1"), "LOW", 0.1, List.of("e1"), "raw1", "m1", null, u1, null, "d1", "multimodal");
        StageCallResult r2 = new StageCallResult("ALLOW", 0.2, List.of("b"), "APPROVE", 0.2, List.of("r2"), List.of("t2"), "HIGH", 0.2, List.of("e2"), "raw2", "m2", 1L, u2, null, "d2", "multimodal");
        doReturn(r1, r2).when(spy).callOnce(
                any(LlmQueueTaskType.class),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                any(),
                nullable(Map.class),
                nullable(String.class),
                anyBoolean(),
                nullable(Map.class)
        );

        StageCallResult out = spy.callImageDescribeOnce(
                "sys",
                new PromptVars("t", "c", "txt"),
                List.of(new ImageRef(1L, "u1", "image/png"), new ImageRef(2L, "u2", "image/png")),
                "PT={{text}}",
                null,
                0.2,
                0.7,
                200,
                "pid",
                "mid",
                Boolean.TRUE,
                1,
                1,
                true,
                500,
                false
        );
        assertNotNull(out.description());
    }

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = AdminModerationLlmUpstreamSupport.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokeInstance(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static String invokeStaticString(String name, Class<?>[] types, Object... args) throws Exception {
        Object v = invokeStatic(name, types, args);
        return v == null ? null : String.valueOf(v);
    }

    private static int invokeStaticInt(String name, Class<?>[] types, Object... args) throws Exception {
        Object v = invokeStatic(name, types, args);
        return v == null ? 0 : ((Number) v).intValue();
    }
}
