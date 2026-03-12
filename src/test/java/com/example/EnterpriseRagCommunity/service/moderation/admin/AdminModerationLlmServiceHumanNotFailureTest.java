package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceHumanNotFailureTest {

    @Test
    void textStageHuman_dueToWhitelist_shouldNotBeTreatedAsFailure() {
        ModerationLlmConfigRepository cfgRepo = mock(ModerationLlmConfigRepository.class);
        ModerationConfidenceFallbackConfigRepository fbRepo = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        PostsRepository postsRepo = mock(PostsRepository.class);
        CommentsRepository commentsRepo = mock(CommentsRepository.class);
        ReportsRepository reportsRepo = mock(ReportsRepository.class);
        PostAttachmentsRepository attRepo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        TagsRepository tagsRepo = mock(TagsRepository.class);
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setMultimodalPromptCode("MODERATION_VISION");
        cfg.setJudgePromptCode("MODERATION_JUDGE");
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("{{text}}");
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("v");
        visionPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(Optional.of(visionPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity judgePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        judgePrompt.setUserPromptTemplate("x");
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(judgePrompt));
        TagsEntity allowed = new TagsEntity();
        allowed.setType(TagType.RISK);
        allowed.setSlug("abuse");
        allowed.setName("abuse");
        allowed.setIsActive(true);
        when(tagsRepo.findByTypeAndIsActiveTrue(TagType.RISK)).thenReturn(List.of(allowed));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        fb.setThresholds(Map.of(
                "llm.cross.upgrade.enable", false,
                "llm.cross.upgrade.onConflict", true,
                "llm.cross.upgrade.onUncertainty", true,
                "llm.cross.upgrade.onGray", true,
                "llm.cross.upgrade.uncertaintyMin", 0.9,
                "llm.cross.upgrade.scoreGrayMargin", 0.05
        ));
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));

        String textRaw = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision_suggestion\\\":\\\"REJECT\\\",\\\"risk_score\\\":0.60,\\\"labels\\\":[\\\"test_label\\\"],\\\"severity\\\":\\\"HIGH\\\",\\\"evidence\\\":[\\\"hello\\\"],\\\"uncertainty\\\":0.1,\\\"reasons\\\":[]}\"}}]}";
        String imageRaw = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision\\\":\\\"APPROVE\\\",\\\"score\\\":0.0,\\\"riskTags\\\":[\\\"safe\\\"],\\\"evidence\\\":[]}\"}}]}";
        String judgeRaw = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision\\\":\\\"APPROVE\\\",\\\"score\\\":0.0,\\\"riskTags\\\":[\\\"safe\\\"],\\\"evidence\\\":[]}\"}}]}";

        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_MODERATION), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(textRaw, "p1", "text-model", null))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(imageRaw, "p1", "vision-model", null));

        AdminModerationLlmService svc = AdminModerationLlmServiceTestFactory.newService(
                cfgRepo,
                fbRepo,
                queueRepo,
                policyConfigRepository,
                postsRepo,
                commentsRepo,
                reportsRepo,
                attRepo,
                fileAssetsRepo,
                fileAssetExtractionsRepository,
                usersRepo,
                tagsRepo,
                promptsRepository,
                webContentFetchService,
                llmGateway,
                auditLogWriter,
                auditDiffBuilder
        );

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText("hello");
        LlmModerationTestRequest.ImageInput img = new LlmModerationTestRequest.ImageInput();
        img.setUrl("https://example.com/a.png");
        req.setImages(List.of(img));

        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertNotNull(resp.getStages());
        assertNotNull(resp.getStages().getText());
        assertNotNull(resp.getStages().getImage());
        assertEquals("HUMAN", resp.getDecision());
        assertTrue(resp.getReasons() == null || resp.getReasons().stream().noneMatch(s -> s != null && s.contains("risk tag not in whitelist")));
        assertEquals("multistage", resp.getInputMode());

        verify(llmGateway, times(2)).chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_MODERATION), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(Map.class));
    }
}


