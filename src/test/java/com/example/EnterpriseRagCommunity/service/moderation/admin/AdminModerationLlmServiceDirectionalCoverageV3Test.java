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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceDirectionalCoverageV3Test {

    @Test
    void test_shouldRejectByTagThresholdInTextStageWithOverrides() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.FALSE);
        req.setImages(List.of(image(" https://img/1.png "), image(""), image("https://img/2.png")));
        LlmModerationTestRequest.LlmModerationConfigOverrideDTO override = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
        override.setVisionPromptTemplate("override-template");
        override.setVisionSystemPrompt("  override-system  ");
        req.setConfigOverride(override);

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(prompt.getVisionMaxImagesPerRequest())).thenReturn(2);
        when(f.imageSupport.resolveImages(req, 2)).thenReturn(java.util.Arrays.asList(
                null,
                new ImageRef(1L, " https://img/1.png ", "image/png"),
                new ImageRef(2L, " ", "image/png"),
                new ImageRef(3L, "https://img/2.png", "image/png")
        ));
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn("trace");
        when(f.contextBuilder.buildPolicyContextBlock(req, false)).thenReturn("policy");
        when(f.contextBuilder.resolveQueueCtx(req, false)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(any(), any(), anyList())).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(tag("violence", "Violence", 0.2)));

        StageCallResult textStage = new StageCallResult("REJECT", 0.3, List.of("violence"), "",
                0.3, null, List.of("violence"), null, null, List.of(), "{}", "m", 1L, null, null, "text", "text");
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("REJECT", out.getDecision());
        assertEquals(List.of("violence"), out.getRiskTags());
    }

    @Test
    void test_shouldFallbackToHumanWhenImageDecisionBlank() {
        Fixture f = fixture();
        ModerationLlmConfigEntity merged = baseConfig();
        PromptsEntity prompt = visionPrompt();
        prompt.setSystemPrompt(null);
        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setUseQueue(Boolean.TRUE);
        req.setImages(List.of(image("https://img/human.png")));

        when(f.configSupport.loadBaseConfigCached()).thenReturn(baseConfig());
        when(f.configSupport.merge(any(), any())).thenReturn(merged);
        when(f.contextBuilder.resolvePromptVarsSafe(req)).thenReturn(new PromptVars("t", "c", "body"));
        when(f.promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(java.util.Optional.of(prompt));
        when(f.imageSupport.clampVisionMaxImages(prompt.getVisionMaxImagesPerRequest())).thenReturn(3);
        List<ImageRef> refs = List.of(new ImageRef(9L, "https://img/human.png", "image/png"));
        when(f.imageSupport.resolveImages(req, 3)).thenReturn(refs);
        when(f.contextBuilder.buildQueueTraceLine(req)).thenReturn(" ");
        when(f.contextBuilder.buildPolicyContextBlock(req, true)).thenReturn(" ");
        when(f.contextBuilder.resolveQueueCtx(req, true)).thenReturn(null);
        when(f.contextBuilder.buildVisionAuditInputJsonList(req, null, refs)).thenReturn("[]");
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of(fallback()));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of());

        StageCallResult textStage = new StageCallResult("APPROVE", 0.1, List.of("safe"), "APPROVE",
                0.1, List.of("t"), List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, "text", "text");
        StageCallResult imageStage = new StageCallResult("ESCALATE", 0.61, List.of("safe"), " ",
                0.61, List.of("i"), List.of("safe"), "MEDIUM", 0.2, List.of("ev"), "{}", "m", 2L, null, null, "image", "image");
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("HUMAN", out.getDecision());
    }

    @Test
    void privateHelpers_shouldCoverDecisionAndFailureFallbacks() throws Exception {
        StageCallResult stage = new StageCallResult("APPROVE", 0.2, List.of("a"), "  ", 0.2, List.of("ok"), List.of("a"), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        assertEquals("APPROVE", invokeStatic("resolveStageDecision", new Class[]{StageCallResult.class, double.class, double.class, boolean.class}, stage, 0.8, 0.5, false));
        assertEquals("REJECT", invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, "x", "REJECT"));
        assertEquals("HUMAN", invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, "x", "HUMAN"));
        assertEquals("APPROVE", invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, "x", "APPROVE"));
        assertEquals("HUMAN", invokeStatic("combineStageDecision", new Class[]{String.class, String.class}, "x", "x"));

        StageCallResult text = new StageCallResult(null, null, List.of(), "REJECT", 0.9, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        StageCallResult image = new StageCallResult(null, null, List.of(), "HUMAN", 0.6, List.of(), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, null);
        assertSame(image, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "REJECT", "APPROVE", "REJECT", text, image));
        assertSame(image, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "HUMAN", "APPROVE", "HUMAN", text, image));
        assertSame(text, invokeStatic("resolveDecisiveStage", new Class[]{String.class, String.class, String.class, StageCallResult.class, StageCallResult.class}, "APPROVE", "APPROVE", "APPROVE", text, null));

        Fixture f = fixture();
        assertFalse((boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, null, List.of("a"), Map.of("a", 0.1)));
        assertFalse((boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.2, null, Map.of("a", 0.1)));
        assertFalse((boolean) invokeInstance(f.service, "exceedsTagThreshold", new Class[]{Double.class, List.class, Map.class}, 0.2, List.of("a"), Map.of()));

        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, " no ", "k"));
        assertFalse((boolean) invokeStatic("asBooleanRequired", new Class[]{Object.class, String.class}, "0", "k"));
        assertTrue((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, List.of(), null, null, List.of(" upstream "), List.of(), null, null, List.of(), "{}", "m", 1L, null, null, null, null)));
        assertFalse((boolean) invokeStatic("isStageCallFailed", new Class[]{StageCallResult.class}, new StageCallResult(null, null, List.of(), null, null, new ArrayList<>(), List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, null, null)));
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
