package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminModerationLlmServicePromptTemplateVarsTest {

    @Test
    void promptTemplate_supportsTitleAndContentVars_fromQueuePost() {
        ModerationLlmConfigRepository cfgRepo = mock(ModerationLlmConfigRepository.class);
        ModerationConfidenceFallbackConfigRepository fbRepo = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        PostsRepository postsRepo = mock(PostsRepository.class);
        CommentsRepository commentsRepo = mock(CommentsRepository.class);
        ReportsRepository reportsRepo = mock(ReportsRepository.class);
        PostAttachmentsRepository attRepo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        AiProperties aiProps = new AiProperties();
        LlmGateway llmGateway = mock(LlmGateway.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setPromptTemplate("标题：\n{{title}}\n\n内容：\n{{content}}");
        cfg.setTemperature(0.2);
        cfg.setThreshold(0.75);
        when(cfgRepo.findAll()).thenReturn(List.of(cfg));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));

        long queueId = 1L;
        long postId = 99L;
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(queueId);
        q.setCaseType(ModerationCaseType.REPORT);
        q.setContentType(ContentType.POST);
        q.setContentId(postId);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        when(queueRepo.findById(queueId)).thenReturn(Optional.of(q));

        PostsEntity p = new PostsEntity();
        p.setId(postId);
        p.setTitle("测试标题");
        p.setContent("测试正文");
        when(postsRepo.findById(postId)).thenReturn(Optional.of(p));

        when(reportsRepo.findByTargetTypeAndTargetId(any(), eq(postId), any(Pageable.class))).thenReturn(Page.empty());

        String assistant = "{\"decision\":\"APPROVE\"}";
        String raw = "{\"choices\":[{\"message\":{\"content\":\"" + assistant.replace("\"", "\\\"") + "\"}}]}";
        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.TEXT_MODERATION), any(), any(), anyList(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null));

        AdminModerationLlmService svc = new AdminModerationLlmService(
                cfgRepo,
                fbRepo,
                queueRepo,
                postsRepo,
                commentsRepo,
                reportsRepo,
                attRepo,
                fileAssetsRepo,
                aiProps,
                llmGateway
        );

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(queueId);
        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertNotNull(resp.getPromptMessages());

        String userPrompt = resp.getPromptMessages().stream()
                .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                .map(LlmModerationTestResponse.Message::getContent)
                .findFirst()
                .orElse("");

        assertTrue(userPrompt.contains("测试标题"));
        assertTrue(userPrompt.contains("测试正文"));
        assertTrue(!userPrompt.contains("{{title}}") && !userPrompt.contains("{{content}}"));
    }

    @Test
    void promptTemplate_supportsContentVar_fromDirectText() {
        ModerationLlmConfigRepository cfgRepo = mock(ModerationLlmConfigRepository.class);
        ModerationConfidenceFallbackConfigRepository fbRepo = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        PostsRepository postsRepo = mock(PostsRepository.class);
        CommentsRepository commentsRepo = mock(CommentsRepository.class);
        ReportsRepository reportsRepo = mock(ReportsRepository.class);
        PostAttachmentsRepository attRepo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        AiProperties aiProps = new AiProperties();
        LlmGateway llmGateway = mock(LlmGateway.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setPromptTemplate("内容：\n{{content}}");
        cfg.setTemperature(0.2);
        cfg.setThreshold(0.75);
        when(cfgRepo.findAll()).thenReturn(List.of(cfg));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));

        String assistant = "{\"decision\":\"APPROVE\"}";
        String raw = "{\"choices\":[{\"message\":{\"content\":\"" + assistant.replace("\"", "\\\"") + "\"}}]}";
        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.TEXT_MODERATION), any(), any(), anyList(), any(), any(), any()))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null));

        AdminModerationLlmService svc = new AdminModerationLlmService(
                cfgRepo,
                fbRepo,
                queueRepo,
                postsRepo,
                commentsRepo,
                reportsRepo,
                attRepo,
                fileAssetsRepo,
                aiProps,
                llmGateway
        );

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText("直传正文");
        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertNotNull(resp.getPromptMessages());

        String userPrompt = resp.getPromptMessages().stream()
                .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                .map(LlmModerationTestResponse.Message::getContent)
                .findFirst()
                .orElse("");

        assertTrue(userPrompt.contains("直传正文"));
        assertTrue(!userPrompt.contains("{{content}}"));
    }
}
