package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerChunkEvidenceCollectTest {

    @SuppressWarnings("unchecked")
    private static List<String> collect(Map<String, Object> mem, int maxItems) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "collectChunkEvidenceForStepDetail",
                Map.class,
                int.class
        );
        m.setAccessible(true);
        return (List<String>) m.invoke(null, mem, maxItems);
    }

    @SuppressWarnings("unchecked")
    private static List<String> summarize(Map<String, Object> mem, Integer chunkIndex, int maxLines) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "summarizeEvidenceMemory",
                Map.class,
                Integer.class,
                int.class
        );
        m.setAccessible(true);
        return (List<String>) m.invoke(null, mem, chunkIndex, maxLines);
    }

    private static String buildFinalReviewInput(Map<String, Object> mem) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "buildFinalReviewInput",
                Map.class
        );
        m.setAccessible(true);
        return (String) m.invoke(null, mem);
    }

    @Test
    void collectKeepsOriginalEvidenceItem_withoutChunkPrefix() throws Exception {
        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("24", List.of("{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\"}"));

        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("llmEvidenceByChunk", byChunk);

        List<String> out = collect(mem, 30);

        assertEquals(1, out.size());
        assertEquals("{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\"}", out.get(0));
    }

    @Test
    void summarizeEvidenceMemory_keepsPlainTextEvidence_notOnlyImagePlaceholder() throws Exception {
        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("24", List.of("{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\",\"text\":\"你这个人真恶心\"}"));

        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("llmEvidenceByChunk", byChunk);

        List<String> out = summarize(mem, 25, 6);

        assertEquals(1, out.size());
        assertTrue(out.get(0).contains("你这个人真恶心"));
    }

    @Test
    void buildFinalReviewInput_fallsBackToChunkEvidence_whenTopLevelEvidenceMissing() throws Exception {
        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
        byChunk.put("24", List.of("{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\",\"text\":\"你这个人真恶心\"}"));

        LinkedHashMap<String, Object> mem = new LinkedHashMap<>();
        mem.put("riskTags", List.of("辱骂攻击"));
        mem.put("llmEvidenceByChunk", byChunk);

        String out = buildFinalReviewInput(mem);

        assertTrue(out.contains("riskTags: [辱骂攻击]"));
        assertTrue(out.contains("evidence: [{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\",\"text\":\"你这个人真恶心\"}]"));
    }

    @Test
    void filterChunkEvidence_shouldKeepOriginalTextEvenWithImagePlaceholder() {
        List<String> out = ModerationLlmAutoRunner.filterChunkEvidence(List.of("该图存在辱骂 [[IMAGE_1]]"));
        assertEquals(1, out.size());
        assertEquals("该图存在辱骂 [[IMAGE_1]]", out.get(0));
    }

    @Test
    void buildEvidenceNormalizeReplay_shouldContainBeforeAfterDiff() {
        List<String> before = List.of("{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\"}");
        List<String> after = List.of("{\"before_context\":\"你这个人\",\"after_context\":\"真恶心\",\"text\":\"你这个人真恶心\"}");

        List<String> replay = ModerationLlmAutoRunner.buildEvidenceNormalizeReplay(before, after, 3);

        assertEquals(1, replay.size());
        assertTrue(replay.get(0).contains("before={\"before_context\":\"你这个人\",\"after_context\":\"真恶心\"}"));
        assertTrue(replay.get(0).contains("after={\"before_context\":\"你这个人\",\"after_context\":\"真恶心\",\"text\":\"你这个人真恶心\"}"));
    }

    private static boolean invokeSuspicious(String text, String chunkText) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "isSuspiciousEvidenceText",
                String.class, String.class
        );
        m.setAccessible(true);
        return (boolean) m.invoke(null, text, chunkText);
    }

    // ──── extractByContextAnchors tests ────

    @Test
    void extractByContextAnchors_findsViolationBetweenAnchors() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "altitude \"banana.\" \n \n ");
        node.put("after_context", "\n\n[[IMAGE_15]]");
        String chunkText = "RNG: ALT SEL - Show or hide the range select altitude \"banana.\" \n \n 蟑螂药、老鼠药、蒙汗药、迷情药+q231456154\n\n[[IMAGE_15]]\n\nmore text";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("蟑螂药、老鼠药、蒙汗药、迷情药+q231456154", result);
    }

    @Test
    void extractByContextAnchors_usesChunkTextOnly() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "banana.");
        node.put("after_context", "IMAGE");
        String chunkText = "banana. VIOLATION_TEXT IMAGE rest";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("VIOLATION_TEXT", result);
    }

    @Test
    void extractByContextAnchors_extractsBetweenAnchors() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "hello ");
        node.put("after_context", " world");
        String chunkText = "hello 违规片段 world";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("违规片段", result);
    }

    @Test
    void extractByContextAnchors_returnsNull_whenBeforeContextMissing() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "not_found_text");
        node.put("after_context", "something");
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, "some chunk text");
        assertTrue(result == null);
    }

    @Test
    void extractByContextAnchors_handlesOnlyBeforeContext() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "prefix:");
        String chunkText = "prefix: violation content here and more stuff";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertTrue(result != null && result.contains("violation content"));
    }

    @Test
    void extractByContextAnchors_fuzzyMatchSmartQuotes() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        // LLM returns straight quotes, but original text has smart quotes
        node.put("before_context", "altitude \"banana.\" \n  ");
        node.put("after_context", "\n\n[[IMAGE_15]]");
        String chunkText = "RNG: ALT SEL - Show or hide the range select altitude \u201cbanana.\u201d \n  \u84d1\u8782\u836f\u3001\u8001\u9f20\u836f\n\n[[IMAGE_15]]\nmore text";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertTrue(result != null && result.contains("\u84d1\u8782\u836f"), "should extract violation via fuzzy quote matching, got: " + result);
    }

    @Test
    void extractByContextAnchors_fuzzyMatchWhitespaceCollapse() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        // LLM collapses double space to single and linebreak to space
        node.put("before_context", "Switch - The battery");
        node.put("after_context", "EMER LIGHTS Emergency");
        // Original text has double space after dash and linebreak before Emergency
        String chunkText = "Battery Master Switch -  The battery \u770b\u770b\u4f60\u4e0b\u9762\nEMER LIGHTS \nEmergency Lights";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertTrue(result != null && result.contains("\u770b\u770b\u4f60\u4e0b\u9762"), "should extract violation via fuzzy whitespace matching, got: " + result);
    }

    @Test
    void extractByContextAnchors_fuzzyMatchSpaceAroundQuotes() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        // LLM drops space before opening curly quote
        node.put("before_context", "altitude\u201cbanana.\u201d\n \n");
        node.put("after_context", "\n\n[[IMAGE_15]]");
        // Original text has space before curly quote
        String chunkText = "altitude \u201cbanana.\u201d \n \n \u8782\u836f\u3001\u8001\u9f20\u836f\n\n[[IMAGE_15]]\nmore";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertTrue(result != null && result.contains("\u8782\u836f"), "should extract via fuzzy space-around-quotes, got: " + result);
    }

    @Test
    void extractByContextAnchors_prefersShortestWindowWhenBeforeRepeated() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "before ");
        node.put("after_context", " after");
        String chunkText = "before 这是很长很长的噪声文本 after xxx before 短片段 after";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("短片段", result);
    }

    @Test
    void isSuspiciousEvidenceText_detectsNotInChunkText() throws Exception {
        boolean suspicious = invokeSuspicious("不在文本里", "正常文本");
        assertTrue(suspicious);

        boolean trusted = invokeSuspicious("看看你下面", "Battery ... 看看你下面 ...");
        assertTrue(!trusted);
    }

    // ──── test.log Sample 1 evidence extraction tests ────

    @Test
    void sample1_evidence1_exactMatchBothAnchors() {
        // Evidence 1: both anchors match exactly → extracts "日你妈"
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "⬤ TERR - Displays relative terrain overlay on HSI.");
        node.put("after_context", "⬤ WX - Displays weather overlay on HSI.");
        String chunkText = "⬤ TERR - Displays relative terrain overlay on HSI. 日你妈\n⬤ WX - Displays weather overlay on HSI.";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("日你妈", result);
    }

    @Test
    void sample1_evidence2_fuzzyAfterContext() {
        // Evidence 2: before_context matches exactly, after_context has whitespace difference
        // (LLM collapsed "\n \nEMER LIGHTS \nEmergency" to "EMER LIGHTS Emergency")
        // Previously fell back to 100-char cap, now should fuzzy-match after_context
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "Battery Master Switch -  The battery master switch supplies main electrical power to the aircraft. ");
        node.put("after_context", "EMER LIGHTS Emergency Lights - The emergency lights provide power to interior lights");
        String chunkText = "Battery Master Switch -  The battery master switch supplies main electrical power to the aircraft. 看看你下面\n \nEMER LIGHTS \nEmergency Lights - The emergency lights provide power to interior lights \n\n[[IMAGE_28]]";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("看看你下面", result);
    }

    @Test
    void sample1_evidence3_imagePlaceholderOnlyBetweenAnchors() {
        // Evidence 3: anchors bracket only [[IMAGE_29]] — not a violation
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "PFD1 is the left,");
        node.put("after_context", "pilot-side PFD. PFD2 is the right,");
        String chunkText = "PFD1 is the left,\n\n[[IMAGE_29]]\n\n pilot-side PFD. PFD2 is the right, co-pilot-side PFD.";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals(null, result);
    }

    @Test
    void extractByContextAnchors_fallbackStopsBeforePromptSectionHeader() {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("before_context", "违规开始:");
        node.put("after_context", "not_found_after");
        String chunkText = "违规开始: 蟑螂药、老鼠药、蒙汗药、迷情药+q231456154\n[OUTPUT_REQUIREMENTS]\nonly prompt section";
        String result = ModerationLlmAutoRunner.extractByContextAnchors(node, chunkText);
        assertEquals("蟑螂药、老鼠药、蒙汗药、迷情药+q231456154", result);
    }
}
