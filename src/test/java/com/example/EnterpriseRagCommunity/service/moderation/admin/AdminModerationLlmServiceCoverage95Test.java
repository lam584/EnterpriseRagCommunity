package com.example.EnterpriseRagCommunity.service.moderation.admin;

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
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceCoverage95Test {

    @Test
    void test_shouldReturnHumanWhenTextStageNull_andCoverPromptBranches() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        prompt.setSystemPrompt(" ");
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);
        req.setImages(List.of(image("https://img/1.png"), image("")));
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setVisionPromptTemplate("override-template");
        req.setConfigOverride(override);

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(3)).thenReturn(2);
        when(f.imageSupport.resolveImages(req, 2)).thenReturn(List.of(new ImageRef(1L, "https://img/1.png", "image/png"), new ImageRef(2L, " ", "image/png")));
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn("trace");
        when(f.contextBuilder.buildPolicyContextBlock(req, true)).thenReturn("policy");
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(any(), any(), anyList())).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag("violence", "Violence", 0.5)));
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(null);

        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("HUMAN", out.getDecision());
        assertNotNull(out.getStages());
    }

    @Test
    void test_shouldCoverImageDecisionFallback_andFinalReasonBranches() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        prompt.setSystemPrompt("sys");
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image("https://img/2.png")));

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(3)).thenReturn(3);
        List<ImageRef> refs = List.of(new ImageRef(2L, "https://img/2.png", "image/png"));
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(" ");
        when(f.contextBuilder.buildPolicyContextBlock(req, false)).thenReturn(" ");
        when(f.contextBuilder.resolveQueueCtx(req, false)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag("violence", "Violence", 0.4)));

        StageCallResult textStage = new StageCallResult("APPROVE", 0.1, List.of("safe"), "APPROVE",
                0.1, null, List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, "text", "text");
        StageCallResult imageStage = new StageCallResult("ESCALATE", 0.95, List.of("violence"), " ",
                0.95, null, List.of("violence"), null, null, List.of(), "{}", "m", 2L, null, null, "image", "image");
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("REJECT", out.getDecision());
        assertEquals(List.of("violence"), out.getRiskTags());
    }

    @Test
    void testImageOnly_shouldCoverOverrideSystemPrompt_andRejectByTagThreshold() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        prompt.setSystemPrompt("base");
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image("https://img/3.png")));
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setVisionPromptTemplate("override-vision");
        override.setVisionSystemPrompt("override-system");
        req.setConfigOverride(override);

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(3)).thenReturn(3);
        List<ImageRef> refs = List.of(new ImageRef(3L, "https://img/3.png", "image/png"));
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(null);
        when(f.contextBuilder.resolveQueueCtx(req, false)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag("violence", "Violence", 0.2)));

        StageCallResult imageStage = new StageCallResult("APPROVE", 0.5, List.of("violence"), "APPROVE",
                0.5, List.of("r"), List.of("violence"), null, null, List.of(), "{}", "m", 2L, null, null, "image", "image");
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.testImageOnly(req);
        assertEquals("REJECT", out.getDecision());
        assertEquals(List.of("violence"), out.getRiskTags());
    }

    @Test
    void test_shouldCoverAdditionalBranchesForThresholdAndAggregation() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        prompt.setSystemPrompt(null);
        prompt.setUserPromptTemplate("vision-template");
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);
        req.setImages(List.of(image("https://img/a.png")));
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setVisionPromptTemplate(" ");
        req.setConfigOverride(override);

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(3)).thenReturn(3);
        List<ImageRef> refs = List.of(new ImageRef(1L, null, "image/png"), new ImageRef(2L, "https://img/a.png", "image/png"));
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(" ");
        when(f.contextBuilder.buildPolicyContextBlock(req, true)).thenReturn("policy");
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));

        TagsEntity longTag = tag("violence", "V".repeat(1300), 0.4);
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(longTag));

        StageCallResult textStage = new StageCallResult("APPROVE", 0.1, List.of("safe"), "APPROVE",
                0.1, null, List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, "text", "text");
        StageCallResult imageStage = new StageCallResult("ESCALATE", 0.2, List.of("violence"), " ",
                null, null, List.of("violence"), null, null, List.of(), "{}", "m", 2L, null, null, "image", "image");
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertNotNull(out.getDecision());
    }

    @Test
    void testImageOnly_shouldCoverNullImagesAndApproveBranches() {
        Fixture f1 = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image("https://img/null.png")));
        when(f1.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f1.configSupport.merge(any(), any())).thenReturn(merged);
        when(f1.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f1.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f1.imageSupport.clampVisionMaxImages(3)).thenReturn(3);
        when(f1.imageSupport.resolveImages(req, 3)).thenReturn(null);
        assertEquals(null, f1.service.testImageOnly(req));

        Fixture f2 = fixture();
        PromptsEntity prompt2 = visionPrompt();
        LlmModerationTestRequest req2 = new LlmModerationTestRequest();
        req2.setUseQueue(Boolean.FALSE);
        req2.setImages(List.of(image("https://img/ok.png")));
        when(f2.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f2.configSupport.merge(any(), any())).thenReturn(baseConfig());
        when(f2.contextBuilder.resolvePromptVarsSafe(req2)).thenReturn(new PromptVars("t", "c", "body"));
        when(f2.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt2));
        when(f2.imageSupport.clampVisionMaxImages(3)).thenReturn(3);
        List<ImageRef> refs2 = List.of(new ImageRef(4L, "https://img/ok.png", "image/png"));
        when(f2.imageSupport.resolveImages(req2, 3)).thenReturn(refs2);
        when(f2.contextBuilder.buildQueueTraceLine(req2)).thenReturn(" ");
        when(f2.contextBuilder.resolveQueueCtx(req2, false)).thenReturn(null);
        when(f2.contextBuilder.buildVisionAuditInputJsonList(req2, null, refs2)).thenReturn("[]");
        when(f2.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f2.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag("violence", "Violence", 0.9)));
        StageCallResult imageStage = new StageCallResult("APPROVE", 0.1, List.of("safe"), "APPROVE",
                0.1, List.of("r"), List.of("safe"), null, null, List.of(), "{}", "m", 2L, null, null, "image", "image");
        when(f2.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(imageStage);

        LlmModerationTestResponse out = f2.service.testImageOnly(req2);
        assertEquals("APPROVE", out.getDecision());
    }

    @Test
    void helperBranches_shouldCoverRemainingArms() throws Exception {
        PromptsEntity p1 = new PromptsEntity();
        p1.setEnableDeepThinking(Boolean.FALSE);
        Object textParams = invokeStatic("resolveTextPromptInvocation", new Class[]{PromptsEntity.class}, p1);
        assertNotNull(textParams);
        PromptsEntity p1b = new PromptsEntity();
        Object textParams2 = invokeStatic("resolveTextPromptInvocation", new Class[]{PromptsEntity.class}, p1b);
        assertNotNull(textParams2);

        PromptsEntity p2 = new PromptsEntity();
        p2.setEnableDeepThinking(Boolean.TRUE);
        Object visionParams = invokeStatic("resolveVisionPromptInvocation", new Class[]{PromptsEntity.class}, p2);
        assertNotNull(visionParams);

        LlmModerationTestResponse.LabelTaxonomy tax = new LlmModerationTestResponse.LabelTaxonomy();
        tax.setAllowedLabels(List.of("Violence"));
        StageCallResult stage = new StageCallResult("REJECT", 0.8, List.of("violence"), "REJECT",
                0.8, List.of("r"), List.of("violence"), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        StageCallResult filtered = AdminModerationLlmService.enforceRiskTagsWhitelist(stage, tax);
        assertEquals("HUMAN", filtered.decision());
        StageCallResult stage2 = new StageCallResult("REJECT", 0.5, null, "REJECT",
                0.5, List.of("r"), null, null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        assertSame(stage2, AdminModerationLlmService.enforceRiskTagsWhitelist(stage2, tax));
        LlmModerationTestResponse.LabelItem bad1 = new LlmModerationTestResponse.LabelItem();
        bad1.setSlug(" ");
        bad1.setName("A");
        LlmModerationTestResponse.LabelItem bad2 = new LlmModerationTestResponse.LabelItem();
        bad2.setSlug("slug");
        bad2.setName(" ");
        LlmModerationTestResponse.LabelTaxonomy tax2 = new LlmModerationTestResponse.LabelTaxonomy();
        tax2.setAllowedLabels(List.of("Violence"));
        tax2.setLabelMap(List.of(bad1, bad2));
        StageCallResult stage3 = new StageCallResult("REJECT", 0.7, List.of("Violence"), "REJECT",
                0.7, List.of("r"), List.of("Violence"), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        assertSame(stage3, AdminModerationLlmService.enforceRiskTagsWhitelist(stage3, tax2));
        LlmModerationTestResponse.LabelItem bad3 = new LlmModerationTestResponse.LabelItem();
        bad3.setSlug(null);
        bad3.setName("A");
        LlmModerationTestResponse.LabelItem bad4 = new LlmModerationTestResponse.LabelItem();
        bad4.setSlug("slug");
        bad4.setName(null);
        LlmModerationTestResponse.LabelTaxonomy tax3 = new LlmModerationTestResponse.LabelTaxonomy();
        tax3.setAllowedLabels(List.of("Violence"));
        tax3.setLabelMap(List.of(bad3, bad4));
        StageCallResult stage4 = new StageCallResult("REJECT", 0.7, List.of("Violence"), "REJECT",
                0.7, List.of("r"), List.of("Violence"), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        assertSame(stage4, AdminModerationLlmService.enforceRiskTagsWhitelist(stage4, tax3));

        assertEquals("REJECT", invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, "REJECT", "APPROVE"));
        assertSame(stage, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "REJECT", "APPROVE", "REJECT", null, stage));
        StageCallResult t = new StageCallResult(null, null, List.of(), "APPROVE", 0.2, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        StageCallResult i = new StageCallResult(null, null, List.of(), "HUMAN", 0.7, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        assertSame(stage, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "REJECT", "APPROVE", "REJECT", t, stage));
        assertSame(i, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "HUMAN", "APPROVE", "HUMAN", t, i));
        assertSame(i, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "REJECT", "APPROVE", "APPROVE", t, i));
        assertSame(i, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "HUMAN", "APPROVE", "APPROVE", t, i));
        assertEquals("APPROVE", invokeStatic("resolveStageDecision", new Class[]{StageCallResult.class, double.class, double.class, boolean.class}, null, 0.8, 0.5, false));
        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "true", "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "false", "k"));
        assertTrue((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "y", "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "n", "k"));
        assertFalse((boolean) invokeInstance(fixture().service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.3, List.of("x"), null));
        assertFalse((boolean) invokeInstance(fixture().service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.3, List.of("x"), Map.of("x", 0.5)));
        assertTrue((boolean) invokeInstance(fixture().service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.9, List.of("x"), Map.of("x", 0.5)));
        assertFalse((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, List.of(), null, null, List.of("ok"), List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, null, null)));

        assertFalse((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, null, null, null, null, null, null, null, List.of(), "{}", "m", 1L, null, null, null, null)));
        assertFalse((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, List.of(), null, null, List.of("ok"), java.util.Arrays.asList((String) null), null, null, List.of(), "{}", "m", 1L, null, null, null, null)));
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, List.of(), null, null, List.of("ok"), List.of("UPSTREAM_ERROR"), null, null, List.of(), "{}", "m", 1L, null, null, null, null)));
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, List.of(), null, null, List.of("could not be parsed as json"), List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, null, null)));
        Fixture f = fixture();
        TagsEntity t1 = new TagsEntity();
        t1.setSlug(" ");
        t1.setName("NameOnly");
        t1.setThreshold(0.4);
        TagsEntity t2e = new TagsEntity();
        t2e.setSlug("slugOnly");
        t2e.setName(" ");
        t2e.setThreshold(0.4);
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(t1, t2e));
        Map<?, ?> thresholds2 = (Map<?, ?>) invokeInstance(f.service, "resolveTagThresholds", new Class[]{});
        assertEquals(2, thresholds2.size());
        LlmModerationTestResponse.LabelTaxonomy taxonomy = (LlmModerationTestResponse.LabelTaxonomy) invokeInstance(f.service, "resolveRiskLabelTaxonomy", new Class[]{});
        assertNotNull(taxonomy);

        Fixture f2 = fixture();
        when(f2.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenThrow(new RuntimeException("boom"));
        Map<?, ?> thresholds = (Map<?, ?>) invokeInstance(f2.service, "resolveTagThresholds", new Class[]{});
    }


    private static Object invokeInstance(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AdminModerationLlmService.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AdminModerationLlmService.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static TagsEntity tag(String slug, String name, Double threshold) {
        TagsEntity t = new TagsEntity();
        t.setSlug(slug);
        t.setName(name);
        t.setThreshold(threshold);
        return t;
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
                fallbackRepository, tagsRepository, promptsRepository, auditLogWriter, auditDiffBuilder,
                imageSupport, contextBuilder, configSupport, upstreamSupport
        );
        return new Fixture(service, fallbackRepository, tagsRepository, promptsRepository, imageSupport, contextBuilder, configSupport, upstreamSupport);
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
        p.setVisionMaxImagesPerRequest(3);
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

    private record Fixture(
            AdminModerationLlmService service,
            ModerationConfidenceFallbackConfigRepository fallbackRepository,
            TagsRepository tagsRepository,
            PromptsRepository promptsRepository,
            AdminModerationLlmImageSupport imageSupport,
            AdminModerationLlmContextBuilder contextBuilder,
            AdminModerationLlmConfigSupport configSupport,
            AdminModerationLlmUpstreamSupport upstreamSupport
    ) {
    }
}
