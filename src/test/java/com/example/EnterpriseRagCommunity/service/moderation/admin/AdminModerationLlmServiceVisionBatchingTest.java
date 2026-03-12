package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
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
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Sort;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminModerationLlmServiceVisionBatchingTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldBatchVisionCallsByTokenBudget_andIncludeMaxPixelsPerImagePart() throws Exception {
        Path uploadRoot = tempDir.resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("2026/02"));

        byte[] a = pngBytes(512, 512);
        byte[] b = pngBytes(512, 512);
        Files.write(uploadRoot.resolve("2026/02/a.png"), a);
        Files.write(uploadRoot.resolve("2026/02/b.png"), b);

        ModerationLlmConfigEntity baseCfg = new ModerationLlmConfigEntity();
        baseCfg.setMultimodalPromptCode("MODERATION_VISION");
        baseCfg.setJudgePromptCode("MODERATION_JUDGE");
        baseCfg.setJudgePromptCode("MODERATION_JUDGE");
        baseCfg.setAutoRun(Boolean.TRUE);

        ModerationLlmConfigRepository cfgRepo = mock(ModerationLlmConfigRepository.class);
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseCfg));
        
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        // Mock Prompts
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("x".repeat(40));
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("y".repeat(40));
        visionPrompt.setSystemPrompt("s");
        visionPrompt.setVisionImageTokenBudget(300);
        visionPrompt.setVisionMaxImagesPerRequest(10);
        visionPrompt.setVisionHighResolutionImages(false);
        visionPrompt.setVisionMaxPixels(1310720);
        when(promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(Optional.of(visionPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity judgePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        judgePrompt.setUserPromptTemplate("z".repeat(40));
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(judgePrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity upgradePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        upgradePrompt.setUserPromptTemplate("u".repeat(40));
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(upgradePrompt));


        ModerationConfidenceFallbackConfigRepository fbRepo = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));
        PostsRepository postsRepo = mock(PostsRepository.class);
        CommentsRepository commentsRepo = mock(CommentsRepository.class);
        ReportsRepository reportsRepo = mock(ReportsRepository.class);
        PostAttachmentsRepository attRepo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        TagsRepository tagsRepo = mock(TagsRepository.class);
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        AtomicInteger imageCalls = new AtomicInteger(0);
        LlmGateway llmGateway = mock(LlmGateway.class, inv -> {
            if ("chatOnceRouted".equals(inv.getMethod().getName())) {
                Object a0 = inv.getArgument(0);
                LlmQueueTaskType taskType = a0 instanceof LlmQueueTaskType t ? t : null;
                if (taskType == LlmQueueTaskType.MULTIMODAL_MODERATION) {
                    int n = imageCalls.incrementAndGet();
                    return new LlmGateway.RoutedChatOnceResult(
                            chatJson("{\"decision\":\"APPROVE\",\"score\":0.02,\"reasons\":[\"i" + n + "\"],\"riskTags\":[],\"description\":\"d" + n + "\"}"),
                            "p",
                            "m",
                            new LlmCallQueueService.UsageMetrics(20, 6, 26, 6)
                    );
                }
                return new LlmGateway.RoutedChatOnceResult(
                        chatJson("{\"decision\":\"APPROVE\",\"score\":0.01,\"reasons\":[\"fallback\"],\"riskTags\":[]}"),
                        "p",
                        "m",
                        new LlmCallQueueService.UsageMetrics(10, 5, 15, 5)
                );
            }
            return Mockito.RETURNS_DEFAULTS.answer(inv);
        });

        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
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

        AdminModerationLlmImageSupport imageSupport = imageSupportOf(svc);
        Field uploadRootField = AdminModerationLlmImageSupport.class.getDeclaredField("uploadRoot");
        uploadRootField.setAccessible(true);
        uploadRootField.set(imageSupport, uploadRoot.toString());
        Field urlPrefixField = AdminModerationLlmImageSupport.class.getDeclaredField("urlPrefix");
        urlPrefixField.setAccessible(true);
        urlPrefixField.set(imageSupport, "/uploads");

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText("hello");
        req.setImages(List.of(imageInput("/uploads/2026/02/a.png"), imageInput("/uploads/2026/02/b.png")));

        var resp = svc.test(req);
        assertNotNull(resp);
        assertEquals("APPROVE", resp.getDecision());
        assertNotNull(resp.getStages());
        assertNotNull(resp.getStages().getImage());
        assertTrue(resp.getStages().getImage().getDescription().contains("[BATCH 1/2]"));
        assertTrue(resp.getStages().getImage().getDescription().contains("[BATCH 2/2]"));

        ArgumentCaptor<List<ChatMessage>> messagesCap = ArgumentCaptor.forClass(List.class);
        verify(llmGateway, times(3)).chatOnceRouted(
            eq(LlmQueueTaskType.MULTIMODAL_MODERATION),
                any(),
                any(),
                messagesCap.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        int imageBatchCalls = 0;
        for (List<ChatMessage> callMessages : messagesCap.getAllValues()) {
            assertNotNull(callMessages);
            assertTrue(callMessages.size() >= 2);
            Object content = callMessages.get(1).content();
            if (!(content instanceof List<?> parts)) {
                continue;
            }
            imageBatchCalls += 1;
            boolean sawMaxPixels = false;
            for (Object p : parts) {
                if (!(p instanceof Map<?, ?> m)) continue;
                Object type = m.get("type");
                if (!"image_url".equals(type)) continue;
                Object v = m.get("max_pixels");
                if (v instanceof Integer vi && vi == 1310720) {
                    sawMaxPixels = true;
                }
            }
            assertTrue(sawMaxPixels);
        }
        assertEquals(2, imageBatchCalls);
        assertEquals(3, imageCalls.get());
    }

    @Test
    void shouldSendVlHighResolutionImagesExtraBodyWhenEnabled() throws Exception {
        Path uploadRoot = tempDir.resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("2026/02"));

        byte[] a = pngBytes(512, 512);
        byte[] b = pngBytes(512, 512);
        Files.write(uploadRoot.resolve("2026/02/a.png"), a);
        Files.write(uploadRoot.resolve("2026/02/b.png"), b);

        ModerationLlmConfigEntity baseCfg = new ModerationLlmConfigEntity();
        baseCfg.setMultimodalPromptCode("MODERATION_VISION");
        baseCfg.setJudgePromptCode("MODERATION_JUDGE");
        baseCfg.setJudgePromptCode("MODERATION_JUDGE");
        baseCfg.setAutoRun(Boolean.TRUE);

        ModerationLlmConfigRepository cfgRepo = mock(ModerationLlmConfigRepository.class);
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseCfg));
        
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        // Mock Prompts
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("x".repeat(40));
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("y".repeat(40));
        visionPrompt.setSystemPrompt("s");
        visionPrompt.setVisionImageTokenBudget(300);
        visionPrompt.setVisionHighResolutionImages(true);
        when(promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(Optional.of(visionPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity judgePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        judgePrompt.setUserPromptTemplate("z".repeat(40));
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(judgePrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity upgradePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        upgradePrompt.setUserPromptTemplate("u".repeat(40));
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(upgradePrompt));


        ModerationConfidenceFallbackConfigRepository fbRepo = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));
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

        AtomicInteger imageCalls = new AtomicInteger(0);
        when(llmGateway.chatOnceRouted(
                any(LlmQueueTaskType.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenAnswer(inv -> {
            LlmQueueTaskType taskType = inv.getArgument(0);
            if (taskType == LlmQueueTaskType.MULTIMODAL_MODERATION) {
                int n = imageCalls.incrementAndGet();
                return new LlmGateway.RoutedChatOnceResult(
                        chatJson("{\"decision\":\"APPROVE\",\"score\":0.02,\"reasons\":[\"i" + n + "\"],\"riskTags\":[],\"description\":\"d" + n + "\"}"),
                        "p",
                        "m",
                        new LlmCallQueueService.UsageMetrics(20, 6, 26, 6)
                );
            }
            throw new IllegalStateException("unexpected taskType");
        });

        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
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

        AdminModerationLlmImageSupport imageSupport = imageSupportOf(svc);
        Field uploadRootField = AdminModerationLlmImageSupport.class.getDeclaredField("uploadRoot");
        uploadRootField.setAccessible(true);
        uploadRootField.set(imageSupport, uploadRoot.toString());
        Field urlPrefixField = AdminModerationLlmImageSupport.class.getDeclaredField("urlPrefix");
        urlPrefixField.setAccessible(true);
        urlPrefixField.set(imageSupport, "/uploads");

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText("hello");
        req.setImages(List.of(imageInput("/uploads/2026/02/a.png"), imageInput("/uploads/2026/02/b.png")));

        var resp = svc.test(req);
        assertNotNull(resp);
        assertEquals("APPROVE", resp.getDecision());

        ArgumentCaptor<Map<String, Object>> extraBodyCap = ArgumentCaptor.forClass(Map.class);
        verify(llmGateway, times(3)).chatOnceRouted(
            eq(LlmQueueTaskType.MULTIMODAL_MODERATION),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                extraBodyCap.capture()
        );
        int imageBatchCalls = 0;
        for (Map<String, Object> body : extraBodyCap.getAllValues()) {
            if (body == null) {
                continue;
            }
            imageBatchCalls += 1;
            assertEquals(Boolean.TRUE, body.get("vl_high_resolution_images"));
        }
        assertEquals(2, imageBatchCalls);
        assertEquals(3, imageCalls.get());
    }

    @Test
    void shouldLimitImagesByVisionMaxImagesPerRequest() throws Exception {
        Path uploadRoot = tempDir.resolve("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot.resolve("2026/02"));

        for (String name : List.of("a", "b", "c", "d", "e", "f")) {
            Files.write(uploadRoot.resolve("2026/02/" + name + ".png"), pngBytes(256, 256));
        }

        ModerationLlmConfigEntity baseCfg = new ModerationLlmConfigEntity();
        baseCfg.setMultimodalPromptCode("MODERATION_VISION");
        baseCfg.setJudgePromptCode("MODERATION_JUDGE");
        baseCfg.setJudgePromptCode("MODERATION_JUDGE");
        baseCfg.setAutoRun(Boolean.TRUE);

        ModerationLlmConfigRepository cfgRepo = mock(ModerationLlmConfigRepository.class);
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(baseCfg));
        
        PromptsRepository promptsRepository = mock(PromptsRepository.class);
        // Mock Prompts
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("x".repeat(40));
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("y".repeat(40));
        visionPrompt.setSystemPrompt("s");
        visionPrompt.setVisionImageTokenBudget(100000);
        visionPrompt.setVisionMaxImagesPerRequest(2);
        visionPrompt.setVisionHighResolutionImages(false);
        when(promptsRepository.findByPromptCode("MODERATION_VISION")).thenReturn(Optional.of(visionPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity judgePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        judgePrompt.setUserPromptTemplate("z".repeat(40));
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(judgePrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity upgradePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        upgradePrompt.setUserPromptTemplate("u".repeat(40));
        when(promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(upgradePrompt));


        ModerationConfidenceFallbackConfigRepository fbRepo = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepo = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setLlmTextRiskThreshold(0.80);
        fb.setLlmImageRiskThreshold(0.30);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.10);
        fb.setLlmCrossModalThreshold(0.75);
        when(fbRepo.findAll(any(Sort.class))).thenReturn(List.of(fb));
        PostsRepository postsRepo = mock(PostsRepository.class);
        CommentsRepository commentsRepo = mock(CommentsRepository.class);
        ReportsRepository reportsRepo = mock(ReportsRepository.class);
        PostAttachmentsRepository attRepo = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepo = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        TagsRepository tagsRepo = mock(TagsRepository.class);
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);

        LlmGateway llmGateway = mock(LlmGateway.class, inv -> {
            if ("chatOnceRouted".equals(inv.getMethod().getName())) {
                Object a0 = inv.getArgument(0);
                LlmQueueTaskType taskType = a0 instanceof LlmQueueTaskType t ? t : null;
                if (taskType == LlmQueueTaskType.MULTIMODAL_MODERATION) {
                    return new LlmGateway.RoutedChatOnceResult(
                            chatJson("{\"decision\":\"APPROVE\",\"score\":0.02,\"reasons\":[\"i\"],\"riskTags\":[],\"description\":\"d\"}"),
                            "p",
                            "m",
                            new LlmCallQueueService.UsageMetrics(20, 6, 26, 6)
                    );
                }
                return new LlmGateway.RoutedChatOnceResult(
                        chatJson("{\"decision\":\"APPROVE\",\"score\":0.01,\"reasons\":[\"fallback\"],\"riskTags\":[]}"),
                        "p",
                        "m",
                        new LlmCallQueueService.UsageMetrics(10, 5, 15, 5)
                );
            }
            return Mockito.RETURNS_DEFAULTS.answer(inv);
        });

        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
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

        AdminModerationLlmImageSupport imageSupport = imageSupportOf(svc);
        Field uploadRootField = AdminModerationLlmImageSupport.class.getDeclaredField("uploadRoot");
        uploadRootField.setAccessible(true);
        uploadRootField.set(imageSupport, uploadRoot.toString());
        Field urlPrefixField = AdminModerationLlmImageSupport.class.getDeclaredField("urlPrefix");
        urlPrefixField.setAccessible(true);
        urlPrefixField.set(imageSupport, "/uploads");

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText("hello");
        req.setImages(List.of(
                imageInput("/uploads/2026/02/a.png"),
                imageInput("/uploads/2026/02/b.png"),
                imageInput("/uploads/2026/02/c.png"),
                imageInput("/uploads/2026/02/d.png"),
                imageInput("/uploads/2026/02/e.png"),
                imageInput("/uploads/2026/02/f.png")
        ));

        var resp = svc.test(req);
        assertNotNull(resp);
        assertEquals("APPROVE", resp.getDecision());

        ArgumentCaptor<List<ChatMessage>> messagesCap = ArgumentCaptor.forClass(List.class);
        verify(llmGateway, times(2)).chatOnceRouted(
            eq(LlmQueueTaskType.MULTIMODAL_MODERATION),
                any(),
                any(),
                messagesCap.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        List<List<ChatMessage>> allCalls = messagesCap.getAllValues();
        List<ChatMessage> callMessages = allCalls.get(allCalls.size() - 1);
        Object content = callMessages.get(1).content();
        assertTrue(content instanceof List);
        List<?> parts = (List<?>) content;
        int images = 0;
        for (Object p : parts) {
            if (!(p instanceof Map<?, ?> m)) continue;
            if ("image_url".equals(m.get("type"))) images += 1;
        }
        assertEquals(2, images);
    }

    private static LlmModerationTestRequest.ImageInput imageInput(String url) {
        LlmModerationTestRequest.ImageInput in = new LlmModerationTestRequest.ImageInput();
        in.setUrl(url);
        in.setMimeType("image/png");
        return in;
    }

    private static byte[] pngBytes(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(img, "png", bos));
            return bos.toByteArray();
        }
    }

    private static String chatJson(String assistantContentJson) {
        return "{\"choices\":[{\"message\":{\"content\":\""
                + assistantContentJson.replace("\"", "\\\"")
                + "\"}}]}";
    }

    private AdminModerationLlmImageSupport imageSupportOf(AdminModerationLlmService svc) {
        try {
            Field f = AdminModerationLlmService.class.getDeclaredField("imageSupport");
            f.setAccessible(true);
            return (AdminModerationLlmImageSupport) f.get(svc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

