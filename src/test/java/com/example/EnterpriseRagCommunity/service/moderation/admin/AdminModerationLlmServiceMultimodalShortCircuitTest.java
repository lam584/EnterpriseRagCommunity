package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
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
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminModerationLlmServiceMultimodalShortCircuitTest {

    @Test
    void strongRejectOnText_skipsImageAndCrossStages() {
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
        cfg.setPromptTemplate("{\"decision\":\"{{text}}\"}");
        cfg.setTemperature(0.2);
        cfg.setThreshold(0.75);
        when(cfgRepo.findAll()).thenReturn(List.of(cfg));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));

        String raw = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision\\\":\\\"REJECT\\\",\\\"score\\\":0.96,\\\"reasons\\\":[\\\"x\\\"],\\\"riskTags\\\":[\\\"AD\\\"]}\"}}]}";
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
        req.setText("hello");
        LlmModerationTestRequest.ImageInput img = new LlmModerationTestRequest.ImageInput();
        img.setUrl("https://example.com/a.png");
        req.setImages(List.of(img));

        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertEquals("REJECT", resp.getDecision());
        assertEquals(0.96, resp.getScore(), 1e-9);
        assertNotNull(resp.getStages());
        assertNotNull(resp.getStages().getText());
        assertEquals("REJECT", resp.getStages().getText().getDecision());

        verify(llmGateway, times(1)).chatOnceRouted(eq(LlmQueueTaskType.TEXT_MODERATION), any(), any(), anyList(), any(), any(), any());
        verify(llmGateway, times(0)).chatOnceRouted(eq(LlmQueueTaskType.IMAGE_MODERATION), any(), any(), anyList(), any(), any(), any());
    }
}

