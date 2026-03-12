package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminModerationLlmServiceSafeSchemaParseTest {

    @Test
    void safeFalse_withoutDecisionOrScore_mapsToReject() {
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

        // Mock Prompts
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

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));

        String assistant = "```json\\n{\\n  \\\"safe\\\": false,\\n  \\\"reason\\\": \\\"unsafe\\\",\\n  \\\"labels\\\": [\\\"porn\\\"]\\n}\\n```";
        String raw = "{\"choices\":[{\"message\":{\"content\":\"" + assistant + "\"}}]}";
        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_MODERATION), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null));

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
        req.setText("x");

        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertEquals("HUMAN", resp.getDecision());
        assertEquals(0.0, resp.getScore(), 1e-9);
        assertTrue(resp.getRiskTags() != null && resp.getRiskTags().contains("porn"));
    }
}

