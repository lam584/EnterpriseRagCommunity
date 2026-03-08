package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
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

final class AdminModerationLlmServiceTestFactory {

    static AdminModerationLlmService newService(
            ModerationLlmConfigRepository configRepository,
            ModerationConfidenceFallbackConfigRepository fallbackRepository,
            ModerationQueueRepository queueRepository,
            ModerationPolicyConfigRepository policyConfigRepository,
            PostsRepository postsRepository,
            CommentsRepository commentsRepository,
            ReportsRepository reportsRepository,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetsRepository fileAssetsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository,
            UsersRepository usersRepository,
            com.example.EnterpriseRagCommunity.repository.content.TagsRepository tagsRepository,
            PromptsRepository promptsRepository,
            WebContentFetchService webContentFetchService,
            LlmGateway llmGateway,
            AuditLogWriter auditLogWriter,
            AuditDiffBuilder auditDiffBuilder
    ) {
        AdminModerationLlmImageSupport imageSupport = new AdminModerationLlmImageSupport(
                queueRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                fileAssetExtractionsRepository,
                null
        );
        com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository moderationActionsRepository =
                org.mockito.Mockito.mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository.class);
        AdminModerationLlmContextBuilder contextBuilder = new AdminModerationLlmContextBuilder(
                queueRepository,
                policyConfigRepository,
                postsRepository,
                commentsRepository,
                reportsRepository,
                moderationActionsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository,
                usersRepository,
                tagsRepository,
                webContentFetchService,
                imageSupport
        );
        AdminModerationLlmConfigSupport configSupport = new AdminModerationLlmConfigSupport(configRepository);
        AdminModerationLlmUpstreamSupport upstreamSupport = new AdminModerationLlmUpstreamSupport(llmGateway, imageSupport);
        return new AdminModerationLlmService(
                fallbackRepository,
                tagsRepository,
                promptsRepository,
                auditLogWriter,
                auditDiffBuilder,
                imageSupport,
                contextBuilder,
                configSupport,
                upstreamSupport
        );
    }
}
