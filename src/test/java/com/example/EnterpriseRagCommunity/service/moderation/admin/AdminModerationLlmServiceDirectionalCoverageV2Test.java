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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceDirectionalCoverageV2Test {

    @Test
    void nullRequest_shouldThrowForTestAndImageOnly() {
        Fixture f = fixture();
        assertThrows(IllegalArgumentException.class, () -> f.service.test(null));
        assertThrows(IllegalArgumentException.class, () -> f.service.testImageOnly(null));
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

        StageCallResult textOnly = new StageCallResult(null, null, null, " ", 0.61, null, List.of("safe"), null, null, List.of("ev"), "{}", "m", 10L, null, null, "d", "text");
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textOnly);

        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("HUMAN", out.getDecision());
        assertEquals("ESCALATE", out.getDecisionSuggestion());
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

        StageCallResult textStage = new StageCallResult("APPROVE", 0.2, List.of(), "APPROVE", 0.2, List.of("text-ok"), List.of("safe"), null, null, List.of(), "{}", "m", 1L, null, null, "text", "text");
        StageCallResult imageStage = new StageCallResult("ESCALATE", 0.9, List.of(), "", 0.9, List.of("could not be parsed as json"), List.of("PARSE_ERROR"), null, null, List.of(), "{}", "m", 1L, null, null, "image", "image");
        when(f.upstreamSupport.callTextOnce(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(textStage);
        when(f.upstreamSupport.callImageDescribeOnce(any(), any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(imageStage);

        LlmModerationTestResponse out = f.service.test(req);
        assertEquals("HUMAN", out.getDecision());
    }

    @Test
    void privateRepoDrivenHelpers_shouldCoverThresholdAndTaxonomyBranches() throws Exception {
        Fixture f = fixture();
        TagsEntity t1 = new TagsEntity();
        t1.setSlug("s1");
        t1.setName("n1");
        t1.setThreshold(0.5);
        TagsEntity t2 = new TagsEntity();
        t2.setSlug(" ");
        t2.setName("n2");
        t2.setThreshold(0.7);
        TagsEntity t3 = new TagsEntity();
        t3.setSlug("s1");
        t3.setName("");
        t3.setThreshold(0.9);
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(java.util.Arrays.asList(null, t1, t2, t3, t1));

        @SuppressWarnings("unchecked")
        Map<String, Double> map = (Map<String, Double>) invokeInstance(f.service, "resolveTagThresholds", new Class[]{});
        assertTrue(map.containsKey("s1"));
        LlmModerationTestResponse.LabelTaxonomy tax = (LlmModerationTestResponse.LabelTaxonomy) invokeInstance(f.service, "resolveRiskLabelTaxonomy", new Class[]{});
        assertEquals("risk_tags", tax.getTaxonomyId());
        when(f.tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK)).thenThrow(new RuntimeException("x"));
        LlmModerationTestResponse.LabelTaxonomy tax2 = (LlmModerationTestResponse.LabelTaxonomy) invokeInstance(f.service, "resolveRiskLabelTaxonomy", new Class[]{});
        assertTrue(tax2.getAllowedLabels() == null || tax2.getAllowedLabels().isEmpty());
    }

    @Test
    void finalizeMultiStage_shouldCoverJudgeImageTextFallbackChains() throws Exception {
        Fixture f = fixture();
        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        LlmModerationTestResponse.Stage judge = new LlmModerationTestResponse.Stage();
        judge.setLabels(List.of("judge-label"));
        judge.setModel("m1");
        judge.setUsage(new LlmModerationTestResponse.Usage());
        stages.setJudge(judge);
        LlmModerationTestResponse resp1 = (LlmModerationTestResponse) invokeInstance(
                f.service,
                "finalizeMultiStage",
                new Class[]{String.class, Double.class, List.class, List.class, LlmModerationTestResponse.Stages.class, List.class, LlmModerationTestResponse.LabelTaxonomy.class, StageCallResult.class},
                "HUMAN", 0.5, List.of(), List.of(), stages, null, null, null
        );
        assertEquals(List.of("judge-label"), resp1.getLabels());

        LlmModerationTestResponse.Stages stages2 = new LlmModerationTestResponse.Stages();
        LlmModerationTestResponse.Stage image = new LlmModerationTestResponse.Stage();
        image.setLabels(List.of("image-label"));
        image.setModel("m2");
        image.setUsage(new LlmModerationTestResponse.Usage());
        stages2.setImage(image);
        LlmModerationTestResponse resp2 = (LlmModerationTestResponse) invokeInstance(
                f.service,
                "finalizeMultiStage",
                new Class[]{String.class, Double.class, List.class, List.class, LlmModerationTestResponse.Stages.class, List.class, LlmModerationTestResponse.LabelTaxonomy.class, StageCallResult.class},
                "APPROVE", 0.1, List.of(), null, stages2, List.of("u"), null, null
        );
        assertEquals(List.of("image-label"), resp2.getLabels());
    }

    @Test
    void loadFallbackRequired_shouldThrowWhenConfigMissing() throws Exception {
        Fixture f = fixture();
        when(f.fallbackRepository.findAll(any(Sort.class))).thenReturn(List.of());
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () -> invokeInstance(f.service, "loadFallbackRequired", new Class[]{}));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
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
            AdminModerationLlmImageSupport imageSupport,
            AdminModerationLlmContextBuilder contextBuilder,
            AdminModerationLlmConfigSupport configSupport,
            AdminModerationLlmUpstreamSupport upstreamSupport
    ) {
    }
}
