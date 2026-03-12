package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.PromptLlmParams;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceDirectionalCoverageTest {

    @Test
    void getConfig_shouldDelegateToConfigSupport() {
        Fixture f = fixture();
        ModerationLlmConfigEntity cfg = baseConfig();
        LlmModerationConfigDTO dto = new LlmModerationConfigDTO();
        dto.setMultimodalPromptCode("T");

        when(f.configSupport.loadBaseConfigCached()).thenReturn(cfg);
        when(f.configSupport.toDto(cfg, null)).thenReturn(dto);

        LlmModerationConfigDTO out = f.service.getConfig();
        assertSame(dto, out);
        verify(f.configSupport).loadBaseConfigCached();
        verify(f.configSupport).toDto(cfg, null);
    }

    @Test
    void upsertConfig_shouldWriteAuditAndReturnDto() {
        Fixture f = fixture();
        ModerationLlmConfigEntity saved = baseConfig();
        saved.setId(88L);
        ConfigUpsertResult upsert = new ConfigUpsertResult(
                saved,
                Map.of("before", "x"),
                Map.of("after", "y")
        );
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        payload.setMultimodalPromptCode("V");
        payload.setJudgePromptCode("J");
        LlmModerationConfigDTO dto = new LlmModerationConfigDTO();
        dto.setId(88L);
        dto.setUpdatedBy("alice");
        Map<String, Object> diff = Map.of("changed", true);

        when(f.configSupport.upsertConfigEntity(payload, 7L)).thenReturn(upsert);
        when(f.auditDiffBuilder.build(upsert.beforeSummary(), upsert.afterSummary())).thenReturn(diff);
        when(f.configSupport.toDto(saved, "alice")).thenReturn(dto);

        LlmModerationConfigDTO out = f.service.upsertConfig(payload, 7L, "alice");
        assertSame(dto, out);
        verify(f.auditLogWriter).write(
                eq(7L),
                eq("alice"),
                eq("CONFIG_CHANGE"),
                eq("MODERATION_LLM_CONFIG"),
                eq(88L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.SUCCESS),
                eq("Updated LLM moderation config"),
                eq(null),
                eq(diff)
        );
    }

    @Test
    void upsertConfig_shouldIgnoreAuditExceptionAndStillReturnDto() {
        Fixture f = fixture();
        ModerationLlmConfigEntity saved = baseConfig();
        ConfigUpsertResult upsert = new ConfigUpsertResult(saved, Map.of(), Map.of());
        LlmModerationConfigDTO payload = new LlmModerationConfigDTO();
        payload.setMultimodalPromptCode("V");
        payload.setJudgePromptCode("J");
        LlmModerationConfigDTO dto = new LlmModerationConfigDTO();
        dto.setMultimodalPromptCode("T");

        when(f.configSupport.upsertConfigEntity(payload, 1L)).thenReturn(upsert);
        doThrow(new RuntimeException("audit")).when(f.auditDiffBuilder).build(any(), any());
        when(f.configSupport.toDto(saved, "u")).thenReturn(dto);

        LlmModerationConfigDTO out = f.service.upsertConfig(payload, 1L, "u");
        assertSame(dto, out);
        verify(f.auditLogWriter, never()).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testImageOnly_shouldReturnNullWhenNoImagesResolved() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity visionPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(visionPrompt));
        when(f.imageSupport.clampVisionMaxImages(visionPrompt.getVisionMaxImagesPerRequest())).thenReturn(3);
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(List.of());
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "x"));
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        LlmModerationTestResponse out = f.service.testImageOnly(req);
        assertNull(out);
        verify(f.upstreamSupport, never()).callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void testImageOnly_shouldRouteHumanWhenImageStageDecisionIsHuman() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity visionPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image(" https://img/1.png ")));

        StageCallResult stage = new StageCallResult(
                "ESCALATE",
                0.8,
                List.of("riskA"),
                "HUMAN",
                0.8,
                List.of("need-human"),
                List.of("riskA"),
                "medium",
                0.4,
                List.of("e1"),
                "raw",
                "m",
                5L,
                null,
                null,
                "desc",
                "image"
        );

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(visionPrompt));
        when(f.imageSupport.clampVisionMaxImages(visionPrompt.getVisionMaxImagesPerRequest())).thenReturn(5);
        when(f.imageSupport.resolveImages(req, 5)).thenReturn(List.of(new ImageRef(1L, " https://img/1.png ", "image/png")));
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(null);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(" trace ");
        when(f.contextBuilder.resolveQueueCtx(req, false)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, List.of(new ImageRef(1L, " https://img/1.png ", "image/png")))).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(stage);

        LlmModerationTestResponse out = f.service.testImageOnly(req);
        assertNotNull(out);
        assertEquals("HUMAN", out.getDecision());
        assertEquals(List.of("UPSTREAM_ERROR"), out.getRiskTags());
        assertEquals("multimodal", out.getInputMode());
        assertEquals(1, out.getImages().size());
        assertEquals("https://img/1.png", out.getImages().get(0));
    }

    @Test
    void testImageOnly_shouldRejectWhenTagThresholdMatched() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity visionPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);
        req.setImages(List.of(image("https://img/2.png")));

        TagsEntity risk = new TagsEntity();
        risk.setSlug("risky");
        risk.setName("risky");
        risk.setThreshold(0.4);

        StageCallResult stage = new StageCallResult(
                "ALLOW",
                0.5,
                List.of("risky"),
                "APPROVE",
                0.5,
                List.of("reason"),
                List.of("risky"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                "d",
                "image"
        );
        List<ImageRef> refs = java.util.Arrays.asList(
                null,
                new ImageRef(2L, "   ", "image/png"),
                new ImageRef(3L, " https://img/2.png ", "image/png")
        );

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(visionPrompt));
        when(f.imageSupport.clampVisionMaxImages(visionPrompt.getVisionMaxImagesPerRequest())).thenReturn(5);
        when(f.imageSupport.resolveImages(req, 5)).thenReturn(refs);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "x"));
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(null);
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(risk));
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(stage);

        LlmModerationTestResponse out = f.service.testImageOnly(req);
        assertNotNull(out);
        assertEquals("REJECT", out.getDecision());
        assertNotNull(out.getStages());
        assertNotNull(out.getStages().getImage());
        assertEquals("REJECT", out.getStages().getImage().getDecision());
        assertEquals(List.of("https://img/2.png"), out.getImages());
    }

    @Test
    void testImageOnly_shouldApproveWhenImageStageIsNull() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity visionPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setImages(List.of(image("https://img/3.png")));

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(visionPrompt));
        when(f.imageSupport.clampVisionMaxImages(visionPrompt.getVisionMaxImagesPerRequest())).thenReturn(5);
        when(f.imageSupport.resolveImages(req, 5)).thenReturn(List.of(new ImageRef(3L, "https://img/3.png", "image/png")));
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "x"));
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(null);
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, List.of(new ImageRef(3L, "https://img/3.png", "image/png")))).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(null);

        LlmModerationTestResponse out = f.service.testImageOnly(req);
        assertNotNull(out);
        assertEquals("APPROVE", out.getDecision());
        assertEquals(List.of(), out.getRiskTags());
        assertEquals(List.of(), out.getReasons());
    }

    @Test
    void privateHelpers_shouldCoverErrorAndFallbackBranches() throws Exception {
        InvocationTargetException ex1 = assertThrows(InvocationTargetException.class, () -> invokeStatic("require01", new Class[]{Double.class, String.class}, null, "k"));
        assertInstanceOf(IllegalStateException.class, ex1.getCause());
        InvocationTargetException ex2 = assertThrows(InvocationTargetException.class, () -> invokeStatic("require01", new Class[]{Double.class, String.class}, 1.2, "k"));
        assertInstanceOf(IllegalStateException.class, ex2.getCause());
        assertEquals(0.0, (double) invokeStatic("clamp01", new Class[]{Double.class, double.class}, -1.0, 0.3));
        assertEquals(1.0, (double) invokeStatic("clamp01", new Class[]{Double.class, double.class}, 9.0, 0.3));
        assertEquals(0.3, (double) invokeStatic("clamp01", new Class[]{Double.class, double.class}, Double.NaN, 0.3));

        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "yes", "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "no", "k"));
        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, 2, "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, 0, "k"));
        InvocationTargetException ex3 = assertThrows(InvocationTargetException.class, () -> invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "?", "k"));
        assertInstanceOf(IllegalStateException.class, ex3.getCause());

        assertEquals(1.25, (double) invokeStatic("asDoubleRequired", new Class[]{Object.class, String.class}, "1.25", "k"));
        InvocationTargetException ex4 = assertThrows(InvocationTargetException.class, () -> invokeStatic("asDoubleRequired", new Class[]{Object.class, String.class}, "not-a-number", "k"));
        assertInstanceOf(IllegalStateException.class, ex4.getCause());

        assertEquals(0.0, (double) invokeStatic("clamp01Strict", new Class[]{double.class}, -2.0));
        assertEquals(1.0, (double) invokeStatic("clamp01Strict", new Class[]{double.class}, 2.0));
        InvocationTargetException ex5 = assertThrows(InvocationTargetException.class, () -> invokeStatic("clamp01Strict", new Class[]{double.class}, Double.POSITIVE_INFINITY));
        assertInstanceOf(IllegalStateException.class, ex5.getCause());

        assertEquals(List.of("a", "b"), invokeStatic("mergeTags", new Class[]{List.class, List.class}, List.of("a"), List.of("a", "b")));
        assertNull(invokeStatic("mergeTags", new Class[]{List.class, List.class}, null, null));

        StageCallResult parseErr = new StageCallResult("x", 0.1, List.of(), "APPROVE", 0.1, List.of(), List.of("PARSE_ERROR"), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, parseErr));
        StageCallResult upstreamErr = new StageCallResult("x", 0.1, List.of(), "APPROVE", 0.1, List.of("upstream bad"), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, upstreamErr));
        StageCallResult ok = new StageCallResult("x", 0.1, List.of(), "APPROVE", 0.1, List.of("good"), List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        assertFalse((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, ok));

        PromptsEntity p = new PromptsEntity();
        p.setProviderId("  ");
        p.setModelName("m1");
        p.setTemperature(0.2);
        p.setTopP(0.9);
        p.setMaxTokens(123);
        p.setEnableDeepThinking(Boolean.TRUE);
        PromptLlmParams textParams = (PromptLlmParams) invokeStatic("resolveTextPromptInvocation", new Class[]{PromptsEntity.class}, p);
        assertNull(textParams.providerId());
        assertEquals("m1", textParams.model());
        assertTrue(textParams.enableThinking());

        PromptsEntity pv = new PromptsEntity();
        pv.setProviderId("p1");
        pv.setModelName("m1");
        pv.setVisionProviderId("vp");
        pv.setVisionModel("vm");
        pv.setTemperature(0.1);
        pv.setTopP(0.8);
        pv.setMaxTokens(50);
        pv.setVisionTemperature(0.3);
        pv.setVisionTopP(0.7);
        pv.setVisionMaxTokens(60);
        pv.setVisionEnableDeepThinking(Boolean.FALSE);
        PromptLlmParams visionParams = (PromptLlmParams) invokeStatic("resolveVisionPromptInvocation", new Class[]{PromptsEntity.class}, pv);
        assertEquals("vp", visionParams.providerId());
        assertEquals("vm", visionParams.model());
        assertFalse(visionParams.enableThinking());

        InvocationTargetException ex6 = assertThrows(InvocationTargetException.class, () -> invokeStatic("resolveTextPromptInvocation", new Class[]{PromptsEntity.class}, new Object[]{null}));
        assertInstanceOf(IllegalStateException.class, ex6.getCause());

        Fixture f = fixture();
        assertTrue((boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.6, List.of("r"), Map.of("r", 0.5)));
        assertFalse((boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.4, List.of("r"), Map.of("r", 0.5)));
        assertFalse((boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, null, List.of("r"), Map.of("r", 0.5)));
    }

    @Test
    void enforceRiskTagsWhitelist_shouldReturnOriginalWhenNoFilteringNeeded() {
        StageCallResult in = new StageCallResult(
                "APPROVE",
                0.1,
                List.of("a"),
                "APPROVE",
                0.1,
                List.of("ok"),
                List.of(),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                null,
                "text"
        );
        LlmModerationTestResponse.LabelTaxonomy tax = new LlmModerationTestResponse.LabelTaxonomy();
        tax.setAllowedLabels(List.of("a"));
        tax.setLabelMap(List.of());

        StageCallResult out = AdminModerationLlmService.enforceRiskTagsWhitelist(in, tax);
        assertSame(in, out);
    }

    @Test
    void test_shouldFallbackDecisionAndSuggestionInTextOnlyBranch() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity multimodalPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "hello"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(multimodalPrompt));
        when(f.imageSupport.clampVisionMaxImages(multimodalPrompt.getVisionMaxImagesPerRequest())).thenReturn(3);
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(List.of());
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(" ");
        when(f.contextBuilder.buildPolicyContextBlock(req, true)).thenReturn(" ");
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, List.of())).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        StageCallResult textOnly = new StageCallResult(
                null,
                null,
                null,
                " ",
                0.61,
                null,
                List.of("safe"),
                null,
                null,
                List.of("ev"),
                "{}",
                "m",
                10L,
                null,
                null,
                "d",
                "text"
        );
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(textOnly);

        LlmModerationTestResponse out = f.service.test(req);
        assertNotNull(out);
        assertEquals("HUMAN", out.getDecision());
        assertEquals("ESCALATE", out.getDecisionSuggestion());
        assertEquals(0.61, out.getRiskScore());
        assertEquals(List.of(), out.getLabels());
    }

    @Test
    void test_shouldReturnHumanWhenImageStageFailed() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity multimodalPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image("https://img/fail.png")));

        List<ImageRef> refs = List.of(new ImageRef(1L, "https://img/fail.png", "image/png"));
        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "ok"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(multimodalPrompt));
        when(f.imageSupport.clampVisionMaxImages(multimodalPrompt.getVisionMaxImagesPerRequest())).thenReturn(3);
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn("trace");
        when(f.contextBuilder.buildPolicyContextBlock(req, false)).thenReturn("policy");
        when(f.contextBuilder.resolveQueueCtx(req, false)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        StageCallResult textStage = new StageCallResult(
                "APPROVE",
                0.2,
                List.of(),
                "APPROVE",
                0.2,
                List.of("text-ok"),
                List.of("safe"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                "text",
                "text"
        );
        StageCallResult imageStage = new StageCallResult(
                "ESCALATE",
                0.9,
                List.of(),
                "",
                0.9,
                List.of("could not be parsed as json"),
                List.of("PARSE_ERROR"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                "image",
                "image"
        );
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertNotNull(out);
        assertEquals("HUMAN", out.getDecision());
        assertNotNull(out.getStages());
        assertNotNull(out.getStages().getImage());
    }

    @Test
    void test_shouldResolveImageDecisionWhenBlankAndDeduplicateReasons() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity multimodalPrompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);
        req.setImages(List.of(image("https://img/ok.png")));

        List<ImageRef> refs = List.of(new ImageRef(2L, " https://img/ok.png ", "image/png"));
        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "ok"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(multimodalPrompt));
        when(f.imageSupport.clampVisionMaxImages(multimodalPrompt.getVisionMaxImagesPerRequest())).thenReturn(5);
        when(f.imageSupport.resolveImages(req, 5)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(null);
        when(f.contextBuilder.buildPolicyContextBlock(req, true)).thenReturn(null);
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        StageCallResult textStage = new StageCallResult(
                "ALLOW",
                0.3,
                List.of("l1"),
                "APPROVE",
                0.3,
                List.of("same", " "),
                List.of("safe"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                "text",
                "text"
        );
        StageCallResult imageStage = new StageCallResult(
                "ALLOW",
                0.55,
                List.of("l2"),
                " ",
                0.55,
                List.of("same", "image-only"),
                List.of("safe"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                2L,
                null,
                null,
                "image",
                "image"
        );
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertNotNull(out);
        assertEquals("HUMAN", out.getDecision());
        assertTrue(out.getReasons().contains("same"));
        assertTrue(out.getReasons().contains("image-only"));
        assertEquals(List.of("https://img/ok.png"), out.getImages());
    }

    @Test
    void privateHelpers_shouldCoverRemainingBranches() throws Exception {
        InvocationTargetException ex1 = assertThrows(InvocationTargetException.class,
                () -> invokeStatic("resolveVisionPromptInvocation", new Class[]{PromptsEntity.class}, new Object[]{null}));
        assertInstanceOf(IllegalStateException.class, ex1.getCause());

        InvocationTargetException ex2 = assertThrows(InvocationTargetException.class,
                () -> invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, null, "k"));
        assertInstanceOf(IllegalStateException.class, ex2.getCause());
        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, Boolean.TRUE, "k"));

        InvocationTargetException ex3 = assertThrows(InvocationTargetException.class,
                () -> invokeStatic("asDoubleRequired", new Class[]{Object.class, String.class}, null, "k"));
        assertInstanceOf(IllegalStateException.class, ex3.getCause());
        assertEquals(2.0, (double) invokeStatic("asDoubleRequired", new Class[]{Object.class, String.class}, 2, "k"));

        StageCallResult noDecisionHigh = new StageCallResult(null, 0.9, List.of(), " ", 0.9, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        String dec1 = (String) invokeStatic("resolveStageDecision", new Class[]{StageCallResult.class, double.class, double.class, boolean.class}, noDecisionHigh, 0.8, 0.5, false);
        assertEquals("REJECT", dec1);
        StageCallResult noDecisionMid = new StageCallResult(null, 0.6, List.of(), null, 0.6, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        String dec2 = (String) invokeStatic("resolveStageDecision", new Class[]{StageCallResult.class, double.class, double.class, boolean.class}, noDecisionMid, 0.8, 0.5, false);
        assertEquals("HUMAN", dec2);
        String dec3 = (String) invokeStatic("resolveStageDecision", new Class[]{StageCallResult.class, double.class, double.class, boolean.class}, noDecisionMid, 0.8, 0.5, true);
        assertEquals("REJECT", dec3);

        assertEquals("HUMAN", invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, null, null));

        StageCallResult ts = new StageCallResult("x", 0.1, List.of(), "REJECT", 0.1, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        StageCallResult is = new StageCallResult("x", 0.2, List.of(), "HUMAN", 0.2, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, "image");
        assertSame(ts, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "REJECT", "REJECT", "APPROVE", ts, is));
        assertSame(is, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "REJECT", "APPROVE", "REJECT", ts, is));
        assertSame(is, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "HUMAN", "APPROVE", "HUMAN", ts, is));
        assertSame(ts, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "APPROVE", "APPROVE", "APPROVE", ts, null));
    }

    @Test
    void test_shouldThrowWhenVarsNullOrTextTooLongOrPromptMissing() {
        Fixture f = fixture();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);

        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> f.service.test(req));

        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "x".repeat(7001)));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(prompt.getVisionMaxImagesPerRequest())).thenReturn(3);
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(List.of());
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn("trace");
        when(f.contextBuilder.buildPolicyContextBlock(req, true)).thenReturn("policy");
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, List.of())).thenReturn("[]");
        ModerationConfidenceFallbackConfigEntity fb = fallback();
        fb.setLlmRejectThreshold(0.6);
        fb.setLlmHumanThreshold(0.9);
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fb));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());
        StageCallResult textStage = new StageCallResult(
                "APPROVE", 0.1, List.of(), "APPROVE", 0.1, List.of("ok"), List.of(),
                null, null, List.of(), "{}", "m", 1L, null, null, "d", "text"
        );
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);
        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("APPROVE", out.getDecision());

        prompt.setUserPromptTemplate(" ");
        req.setConfigOverride(null);
        assertThrows(IllegalStateException.class, () -> f.service.test(req));
    }

    @Test
    void test_shouldCoverImageDecisionApproveAndImageNullFailure() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image("https://img/a.png")));
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setVisionPromptTemplate("override");
        override.setVisionSystemPrompt(" ");
        req.setConfigOverride(override);

        List<ImageRef> refs = java.util.Arrays.asList(null, new ImageRef(1L, " ", "image/png"), new ImageRef(2L, " https://img/a.png ", "image/png"));
        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "ok"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(prompt.getVisionMaxImagesPerRequest())).thenReturn(5);
        when(f.imageSupport.resolveImages(req, 5)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(" ");
        when(f.contextBuilder.buildPolicyContextBlock(req, false)).thenReturn(" ");
        when(f.contextBuilder.resolveQueueCtx(req, false)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());
        StageCallResult textStage = new StageCallResult(
                "APPROVE", 0.2, List.of("a"), "APPROVE", 0.2, null, List.of(),
                null, null, List.of(), "{}", "m", 1L, null, null, "d", "text"
        );
        StageCallResult imageStage = new StageCallResult(
                "APPROVE", 0.1, List.of("b"), "", 0.1, java.util.Arrays.asList(null, " ", "r2"), List.of(),
                null, null, List.of(), "{}", "m", 2L, null, null, "d", "image"
        );
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(imageStage);
        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("APPROVE", out.getDecision());
        assertEquals(List.of("https://img/a.png"), out.getImages());

        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(null);
        LlmModerationTestResponse fail = f.service.test(req);
        assertEquals("HUMAN", fail.getDecision());
    }

    @Test
    void testImageOnly_shouldCoverOverridesPromptExceptionAndBreak() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        prompt.setSystemPrompt(null);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);
        req.setImages(java.util.stream.IntStream.range(0, 7).mapToObj(i -> image("https://img/" + i + ".png")).toList());
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setVisionPromptTemplate("override-template");
        override.setVisionSystemPrompt(null);
        req.setConfigOverride(override);

        List<ImageRef> refs = new java.util.ArrayList<>();
        refs.add(new ImageRef(0L, null, "image/png"));
        refs.add(new ImageRef(1L, " ", "image/png"));
        for (int i = 0; i < 7; i++) refs.add(new ImageRef((long) (10 + i), " https://img/" + i + ".png ", "image/png"));

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "x"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(prompt.getVisionMaxImagesPerRequest())).thenReturn(9);
        when(f.imageSupport.resolveImages(req, 9)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn("trace");
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());
        StageCallResult imageStage = new StageCallResult(
                "APPROVE", 0.1, List.of(), "APPROVE", null, null, null,
                null, null, List.of(), "{}", "m", 1L, null, null, "d", "image"
        );
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(imageStage);
        LlmModerationTestResponse out = f.service.testImageOnly(req);
        assertEquals("APPROVE", out.getDecision());
        assertEquals(5, out.getImages().size());

        req.setConfigOverride(null);
        prompt.setUserPromptTemplate(" ");
        assertThrows(IllegalStateException.class, () -> f.service.testImageOnly(req));
    }

    @Test
    void privateHelpers_shouldCoverAdditionalMissingBranches() throws Exception {
        assertEquals(false, invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, "APPROVE", "APPROVE").equals("HUMAN"));
        assertEquals("APPROVE", invokeStatic("resolveStageDecision", new Class[]{StageCallResult.class, double.class, double.class, boolean.class},
                new StageCallResult(null, 0.2, List.of(), " ", 0.2, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, "text"), 0.8, 0.5, false));
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new Object[]{null}));
        StageCallResult blocked = new StageCallResult("x", 0.2, List.of(), "APPROVE", 0.2, List.of("ok"), List.of("PROVIDER_OUTPUT_BLOCKED"), null, null, List.of(), "{}", "m", 1L, null, null, null, "text");
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, blocked));
        assertEquals(0.3, (double) invokeStatic("clamp01Strict", new Class[]{double.class}, 0.3));
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> invokeStatic("require01", new Class[]{Double.class, String.class}, Double.NaN, "k"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void finalizeMultiStageOverloadAndTextFallback_shouldBeCovered() throws Exception {
        Fixture f = fixture();
        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        LlmModerationTestResponse.Stage text = new LlmModerationTestResponse.Stage();
        text.setLabels(List.of("t"));
        text.setSeverity("s");
        text.setUncertainty(0.2);
        text.setEvidence(List.of("e"));
        text.setRawModelOutput("r");
        text.setModel("m");
        text.setLatencyMs(1L);
        text.setUsage(new LlmModerationTestResponse.Usage());
        stages.setText(text);
        LlmModerationTestResponse out = (LlmModerationTestResponse) invokeInstance(
                f.service,
                "finalizeMultiStage",
                new Class[]{String.class, Double.class, List.class, List.class, LlmModerationTestResponse.Stages.class, List.class},
                "APPROVE", 0.1, List.of(), List.of(), stages, null
        );
        assertEquals("multistage", out.getInputMode());
        assertEquals("m", out.getModel());
    }

    @Test
    void testAndImageOnly_shouldThrowWhenPromptMissingAndBlankText() {
        Fixture f = fixture();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(baseConfig());
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "   "));
        assertThrows(IllegalArgumentException.class, () -> f.service.test(req));

        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "ok"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.empty());
        assertThrows(IllegalStateException.class, () -> f.service.test(req));

        req.setImages(List.of(image("https://img/a.png")));
        assertThrows(IllegalStateException.class, () -> f.service.testImageOnly(req));
    }

    @Test
    void enforceRiskTagsWhitelist_shouldCoverNullAndNonRejectBranches() {
        LlmModerationTestResponse.LabelTaxonomy tax = new LlmModerationTestResponse.LabelTaxonomy();
        tax.setAllowedLabels(java.util.Arrays.asList(null, " ", "A"));
        LlmModerationTestResponse.LabelItem bad = new LlmModerationTestResponse.LabelItem();
        bad.setSlug(" ");
        bad.setName("B");
        LlmModerationTestResponse.LabelItem map = new LlmModerationTestResponse.LabelItem();
        map.setSlug("slug-x");
        map.setName("MappedX");
        LlmModerationTestResponse.LabelItem bad2 = new LlmModerationTestResponse.LabelItem();
        bad2.setSlug("slug-y");
        bad2.setName(" ");
        tax.setLabelMap(java.util.Arrays.asList(null, bad, map, bad2));

        StageCallResult in = new StageCallResult(
                "ESCALATE",
                0.2,
                List.of("A"),
                "APPROVE",
                0.2,
                null,
                java.util.Arrays.asList(null, " ", "slug-x", "unknown"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                "d",
                "text"
        );
        StageCallResult out = AdminModerationLlmService.enforceRiskTagsWhitelist(in, tax);
        assertNotNull(out);
        assertEquals(List.of("MappedX"), out.riskTags());
        assertEquals("ESCALATE", out.decisionSuggestion());
        assertEquals("APPROVE", out.decision());
        assertNotNull(out.reasons());
    }

    @Test
    void enforceRiskTagsWhitelist_shouldCoverDirectReturnBranches() {
        StageCallResult base = new StageCallResult(
                "REJECT",
                0.9,
                List.of("x"),
                "REJECT",
                0.9,
                List.of("r"),
                null,
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                "d",
                "text"
        );
        assertSame(base, AdminModerationLlmService.enforceRiskTagsWhitelist(base, null));
        LlmModerationTestResponse.LabelTaxonomy t1 = new LlmModerationTestResponse.LabelTaxonomy();
        t1.setAllowedLabels(null);
        assertSame(base, AdminModerationLlmService.enforceRiskTagsWhitelist(base, t1));
        LlmModerationTestResponse.LabelTaxonomy t2 = new LlmModerationTestResponse.LabelTaxonomy();
        t2.setAllowedLabels(List.of());
        assertSame(base, AdminModerationLlmService.enforceRiskTagsWhitelist(base, t2));
    }

    @Test
    void finalizeMultiStage_shouldCoverNullStagesAndUpgradeMode() throws Exception {
        Fixture f = fixture();
        LlmModerationTestResponse out1 = (LlmModerationTestResponse) invokeInstance(
                f.service,
                "finalizeMultiStage",
                new Class[]{String.class, Double.class, List.class, List.class, LlmModerationTestResponse.Stages.class, List.class, LlmModerationTestResponse.LabelTaxonomy.class, StageCallResult.class},
                "HUMAN", 0.5, List.of(), null, null, null, null, null
        );
        assertEquals("multimodal", out1.getInputMode());
        assertEquals(List.of(), out1.getLabels());

        LlmModerationTestResponse.Stages s = new LlmModerationTestResponse.Stages();
        s.setUpgrade(new LlmModerationTestResponse.Stage());
        LlmModerationTestResponse out2 = (LlmModerationTestResponse) invokeInstance(
                f.service,
                "finalizeMultiStage",
                new Class[]{String.class, Double.class, List.class, List.class, LlmModerationTestResponse.Stages.class, List.class, LlmModerationTestResponse.LabelTaxonomy.class, StageCallResult.class},
                "APPROVE", 0.1, List.of(), List.of(), s, List.of(), null, null
        );
        assertEquals("multistage", out2.getInputMode());
    }

    @Test
    void privateThresholdHelpers_shouldCoverRemainingPrimitiveBranches() throws Exception {
        InvocationTargetException ex1 = assertThrows(InvocationTargetException.class, () -> invokeStatic("require01", new Class[]{Double.class, String.class}, -0.1, "k"));
        assertInstanceOf(IllegalStateException.class, ex1.getCause());
        assertEquals(0.7, (double) invokeStatic("clamp01", new Class[]{Double.class, double.class}, null, 0.7));

        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "1", "k"));
        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "y", "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "0", "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "n", "k"));

        Fixture f = fixture();
        @SuppressWarnings("unchecked")
        Boolean b1 = (Boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, null, List.of("a"), Map.of("a", 0.3));
        @SuppressWarnings("unchecked")
        Boolean b2 = (Boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.4, java.util.Arrays.asList((String) null), Map.of("a", 0.3));
        @SuppressWarnings("unchecked")
        Boolean b3 = (Boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.2, List.of("a"), Map.of("a", 0.3));
        assertFalse(b1);
        assertFalse(b2);
        assertFalse(b3);
    }

    private static Fixture fixture() {
        ModerationConfidenceFallbackConfigRepository fallbackRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        AdminModerationLlmImageSupport imageSupport = mock(AdminModerationLlmImageSupport.class);
        AdminModerationLlmContextBuilder contextBuilder = mock(AdminModerationLlmContextBuilder.class);
        AdminModerationLlmConfigSupport configSupport = mock(AdminModerationLlmConfigSupport.class);
        AdminModerationLlmUpstreamSupport upstreamSupport = mock(AdminModerationLlmUpstreamSupport.class);
        AdminModerationLlmService service = new AdminModerationLlmService(
                fallbackRepository,
                tagsRepository,
                promptsRepository,
                auditLogWriter,
                auditDiffBuilder,
                imageSupport,
                contextBuilder,
                configSupport,
                upstreamSupport
        );
        return new Fixture(
                service,
                fallbackRepository,
                tagsRepository,
                promptsRepository,
                auditLogWriter,
                auditDiffBuilder,
                imageSupport,
                contextBuilder,
                configSupport,
                upstreamSupport
        );
    }

    private static ModerationLlmConfigEntity baseConfig() {
        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setMultimodalPromptCode("MODERATION_VISION");
        cfg.setJudgePromptCode("MODERATION_JUDGE");
        cfg.setAutoRun(Boolean.TRUE);
        return cfg;
    }

    private static PromptsEntity visionPrompt() {
        PromptsEntity p = new PromptsEntity();
        p.setSystemPrompt("system");
        p.setUserPromptTemplate("vision template");
        p.setVisionMaxImagesPerRequest(5);
        p.setVisionImageTokenBudget(300);
        p.setVisionHighResolutionImages(Boolean.FALSE);
        p.setVisionMaxPixels(1024);
        return p;
    }

    private static ModerationConfidenceFallbackConfigEntity fallback() {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmImageRiskThreshold(0.9);
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.8);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.1);
        fb.setLlmCrossModalThreshold(0.75);
        return fb;
    }

    private static LlmModerationTestRequest.ImageInput image(String url) {
        LlmModerationTestRequest.ImageInput i = new LlmModerationTestRequest.ImageInput();
        i.setUrl(url);
        i.setMimeType("image/png");
        return i;
    }

    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AdminModerationLlmService.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokeInstance(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AdminModerationLlmService.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private record Fixture(
            AdminModerationLlmService service,
            ModerationConfidenceFallbackConfigRepository fallbackRepository,
            TagsRepository tagsRepository,
            PromptsRepository promptsRepository,
            AuditLogWriter auditLogWriter,
            AuditDiffBuilder auditDiffBuilder,
            AdminModerationLlmImageSupport imageSupport,
            AdminModerationLlmContextBuilder contextBuilder,
            AdminModerationLlmConfigSupport configSupport,
            AdminModerationLlmUpstreamSupport upstreamSupport
    ) {
    }
}
