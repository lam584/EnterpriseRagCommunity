package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
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
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class AdminModerationLlmServiceConfigCacheTest {

    @Test
    void shouldCacheBaseConfigWithinTtl() {
        ModerationLlmConfigRepository configRepository = mock(ModerationLlmConfigRepository.class);
        ModerationConfidenceFallbackConfigRepository fallbackRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        ReportsRepository reportsRepository = mock(ReportsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        WebContentFetchService webContentFetchService = mock(WebContentFetchService.class);
        LlmGateway llmGateway = mock(LlmGateway.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        AuditDiffBuilder auditDiffBuilder = mock(AuditDiffBuilder.class);
        PromptsRepository promptsRepository = mock(PromptsRepository.class);

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setId(1L);
        cfg.setTextPromptCode("x");
        cfg.setVisionPromptCode("x");
        cfg.setJudgePromptCode("x");
        cfg.setJudgePromptCode("x");
        cfg.setUpdatedAt(LocalDateTime.now());

        when(configRepository.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        AdminModerationLlmService service = AdminModerationLlmServiceTestFactory.newService(
                configRepository,
                fallbackRepository,
                queueRepository,
                policyConfigRepository,
                postsRepository,
                commentsRepository,
                reportsRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                usersRepository,
                tagsRepository,
                promptsRepository,
                webContentFetchService,
                llmGateway,
                auditLogWriter,
                auditDiffBuilder
        );

        service.getConfig();
        service.getConfig();

        verify(configRepository, times(1)).findTopByOrderByUpdatedAtDescIdDesc();
    }
}

