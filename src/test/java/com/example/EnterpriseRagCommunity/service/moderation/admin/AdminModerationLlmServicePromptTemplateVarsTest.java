package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
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
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminModerationLlmServicePromptTemplateVarsTest {

    @Test
    void promptTemplate_supportsTitleAndContentVars_fromQueuePost() {
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
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setMultimodalPromptCode("MODERATION_VISION");
        cfg.setJudgePromptCode("MODERATION_JUDGE");
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        // Mock Prompts
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("TITLE={{title}}\n\nBODY={{content}}");
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("TITLE={{title}}\n\nBODY={{content}}");
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
        p.setTitle("test title");
        p.setContent("test content");
        when(postsRepo.findById(postId)).thenReturn(Optional.of(p));

        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(postId);
        att.setFileAssetId(555L);
        when(attRepo.findByPostId(eq(postId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(att)));

        when(reportsRepo.findByTargetTypeAndTargetId(any(), eq(postId), any(Pageable.class))).thenReturn(Page.empty());

        String assistant = "{\"decision\":\"APPROVE\"}";
        String raw = "{\"choices\":[{\"message\":{\"content\":\"" + assistant.replace("\"", "\\\"") + "\"}}]}";
        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_MODERATION), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null));

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

        String systemPrompt = resp.getPromptMessages().stream()
                .filter(m -> m != null && "system".equalsIgnoreCase(m.getRole()))
                .map(LlmModerationTestResponse.Message::getContent)
                .findFirst()
                .orElse("");

        assertTrue(userPrompt.contains("test title"));
        assertTrue(userPrompt.contains("test content"));
        assertTrue(!userPrompt.contains("{{title}}") && !userPrompt.contains("{{content}}"));
        assertTrue(systemPrompt.contains("TRACE"));
        assertTrue(systemPrompt.contains("postId=" + postId));
        assertTrue(systemPrompt.contains("fileAssetIds=[555"));
    }

    @Test
    void promptTemplate_supportsContentVar_fromDirectText() {
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
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        TagsRepository tagsRepo = mock(TagsRepository.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setMultimodalPromptCode("MODERATION_VISION");
        cfg.setJudgePromptCode("MODERATION_JUDGE");
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        // Mock Prompts
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("BODY={{content}}");
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("BODY={{content}}");
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

        String assistant = "{\"decision\":\"APPROVE\"}";
        String raw = "{\"choices\":[{\"message\":{\"content\":\"" + assistant.replace("\"", "\\\"") + "\"}}]}";
        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_MODERATION), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null));

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

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setText("direct text body");
        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertNotNull(resp.getPromptMessages());

        String userPrompt = resp.getPromptMessages().stream()
                .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                .map(LlmModerationTestResponse.Message::getContent)
                .findFirst()
                .orElse("");

        assertTrue(userPrompt.contains("direct text body"));
        assertTrue(!userPrompt.contains("{{content}}"));
    }

    @Test
    void postFilesBlock_includesDerivedImageUrls_andImageStageUsesThem() {
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
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        UsersRepository usersRepo = mock(UsersRepository.class);
        TagsRepository tagsRepo = mock(TagsRepository.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setMultimodalPromptCode("MODERATION_VISION");
        cfg.setJudgePromptCode("MODERATION_JUDGE");
        when(cfgRepo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        // Mock Prompts
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        textPrompt.setUserPromptTemplate("TITLE={{title}}\n\nBODY={{content}}");
        textPrompt.setSystemPrompt("s");
        when(promptsRepository.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        visionPrompt.setUserPromptTemplate("闂傚倸鍊峰ù鍥х暦閸偅鍙忛柡澶嬪殮濞差亶鏁囬柕蹇曞Х閸濇姊洪崨濠庢畼闁稿孩鍔欏畷鐢稿即閵忥紕鍘电紓浣割儏閻忔繈顢楅姀鐘嗙懓鈹冮崹顔瑰亾濠靛钃熼柍鈺佸暙缁剁偤鎮楅敐搴濇喚闁搞倕顑囩槐鎾存媴缁嬪簱鍋撻幖浣瑰亱闁规崘顕ч拑鐔兼煥濠靛棙鍟掗柡鍐ㄧ墕閻掑灚銇勯幒鍡椾壕闁绘挶鍊濋弻鏇㈠醇濠垫劖效闂佹悶鍊栭崝娆撳蓟閿濆绫嶉柛灞捐壘娴犳绱撴担鎻掍壕闂佺硶鍓濈粙鎺楁偂韫囨稒鐓曟い鎰剁悼缁犳牗銇勯妷褍浠遍柡宀嬬秮楠炴帡寮撮悤浣诡棄婵＄偑鍊ゆ禍婊堝疮娴兼潙鐒垫い鎺戯功缁夐潧霉濠婂簼閭€规洩缍佸畷鐔碱敆閸屾粠鍟庨梻浣告啞娓氭宕板Δ鈧悾椋庝沪鐟欙絾鏂€闂佹枼鏅涢崯顖滀焊閿旈敮鍋撶憴鍕閻㈩垱甯￠敐鐐测攽閸ャ劎绉堕梺鍐叉惈閸婄鈻嶈濮婄粯鎷呯粵瀣闁诲孩绋堥弲婵堝垝濞嗘挸绠虫俊銈傚亾缂佲偓婢舵劖鐓涢柛銉ｅ劚閻忊晠鏌ょ粙璺ㄧШ闁诡喗顨婇幃浠嬫偨閻愬厜鍋撴繝鍥ㄧ厱閻庯綆鍋呭畷宀€鈧娲滈崗姗€銆佸鈧幃銏ゆ煥鐎ｎ剚娈繝纰夌磿閸嬫垿宕愰幇鏉挎瀬妞ゅ繐鐗嗙粈瀣亜閹惧鈯曢柣蹇撳暣濮婅櫣鎷犻幓鎺戞瘣缂備浇灏慨銈夊疾閼稿灚鍎熼柨婵嗘川楠炴挸鈹戦纭峰伐妞も晝婀content}}");
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

        long queueId = 2L;
        long postId = 100L;
        long fileAssetId = 556L;
        String derivedUrl = "/uploads/derived-images/2026/02/x.png";

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
        p.setTitle("test title");
        p.setContent("test content");
        when(postsRepo.findById(postId)).thenReturn(Optional.of(p));

        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(postId);
        att.setFileAssetId(fileAssetId);
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(fileAssetId);
        fa.setOriginalName("a.pdf");
        fa.setMimeType("application/pdf");
        fa.setUrl("/uploads/a.pdf");
        att.setFileAsset(fa);
        when(attRepo.findByPostId(eq(postId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(att)));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(fileAssetId);
        ex.setExtractStatus(FileAssetExtractionStatus.READY);
        ex.setExtractedText("pdf text [[IMAGE_1]]");
        ex.setExtractedMetadataJson("{\"extractedImages\":[{\"url\":\"" + derivedUrl + "\",\"mimeType\":\"image/png\"}],\"imageCount\":1}");
        when(fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));

        when(reportsRepo.findByTargetTypeAndTargetId(any(), eq(postId), any(Pageable.class))).thenReturn(Page.empty());

        String pass = "{\"decision\":\"APPROVE\",\"score\":0.0}";
        String raw = "{\"choices\":[{\"message\":{\"content\":\"" + pass.replace("\"", "\\\"") + "\"}}]}";
        when(llmGateway.chatOnceRouted(eq(LlmQueueTaskType.MULTIMODAL_MODERATION), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(raw, "p1", "vision-model", null));

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

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(queueId);
        LlmModerationTestResponse resp = svc.test(req);
        assertNotNull(resp);
        assertNotNull(resp.getPromptMessages());
        assertNotNull(resp.getImages());

        assertTrue(resp.getPromptMessages().stream()
                .filter(m -> m != null && "user".equalsIgnoreCase(m.getRole()))
                .map(LlmModerationTestResponse.Message::getContent)
                .anyMatch(content -> content != null && content.contains(derivedUrl)));
        assertTrue(resp.getImages().contains(derivedUrl));
    }
}

