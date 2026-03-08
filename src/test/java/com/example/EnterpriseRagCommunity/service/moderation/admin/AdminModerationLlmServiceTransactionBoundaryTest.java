package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
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
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.moderation.web.WebContentFetchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AdminModerationLlmServiceTransactionBoundaryTest.TestConfig.class)
public class AdminModerationLlmServiceTransactionBoundaryTest {

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new DummyTxManager();
        }

        static class DummyTxManager extends AbstractPlatformTransactionManager {
            private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> Boolean.FALSE);

            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected boolean isExistingTransaction(Object transaction) {
                return Boolean.TRUE.equals(active.get());
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                active.set(Boolean.TRUE);
            }

            @Override
            protected Object doSuspend(Object transaction) {
                boolean was = Boolean.TRUE.equals(active.get());
                active.set(Boolean.FALSE);
                return was;
            }

            @Override
            protected void doResume(Object transaction, Object suspendedResources) {
                if (suspendedResources instanceof Boolean b) {
                    active.set(b);
                } else {
                    active.set(Boolean.FALSE);
                }
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                active.set(Boolean.FALSE);
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                active.set(Boolean.FALSE);
            }
        }

        @Bean
        PromptsRepository promptsRepository() {
            PromptsRepository repo = mock(PromptsRepository.class);
            
            com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity textPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
            textPrompt.setUserPromptTemplate("{{text}}");
            textPrompt.setSystemPrompt("s");
            when(repo.findByPromptCode("MODERATION_TEXT")).thenReturn(Optional.of(textPrompt));

            com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
            visionPrompt.setUserPromptTemplate("v");
            visionPrompt.setSystemPrompt("s");
            when(repo.findByPromptCode("MODERATION_VISION")).thenReturn(Optional.of(visionPrompt));

            com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity judgePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
            judgePrompt.setUserPromptTemplate("x");
            when(repo.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(judgePrompt));

            com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity upgradePrompt = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
            upgradePrompt.setUserPromptTemplate("u");
            when(repo.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(upgradePrompt));
            
            return repo;
        }

        @Bean
        ModerationLlmConfigRepository configRepository() {
            ModerationLlmConfigRepository repo = mock(ModerationLlmConfigRepository.class);
            ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
            cfg.setTextPromptCode("MODERATION_TEXT");
            cfg.setVisionPromptCode("MODERATION_VISION");
            cfg.setJudgePromptCode("MODERATION_JUDGE");
            cfg.setJudgePromptCode("MODERATION_JUDGE");
            when(repo.findTopByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));
            return repo;
        }

        @Bean
        ModerationConfidenceFallbackConfigRepository fallbackRepository() {
            ModerationConfidenceFallbackConfigRepository repo = mock(ModerationConfidenceFallbackConfigRepository.class);
            var fb = new com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity();
            fb.setLlmRejectThreshold(0.75);
            fb.setLlmHumanThreshold(0.5);
            fb.setLlmTextRiskThreshold(0.80);
            fb.setLlmImageRiskThreshold(0.30);
            fb.setLlmStrongRejectThreshold(0.95);
            fb.setLlmStrongPassThreshold(0.10);
            fb.setLlmCrossModalThreshold(0.75);
            when(repo.findAll(any(Sort.class))).thenReturn(List.of(fb));
            return repo;
        }

        @Bean
        ModerationQueueRepository queueRepository() {
            return mock(ModerationQueueRepository.class);
        }

        @Bean
        ModerationPolicyConfigRepository moderationPolicyConfigRepository() {
            return mock(ModerationPolicyConfigRepository.class);
        }

        @Bean
        PostsRepository postsRepository() {
            return mock(PostsRepository.class);
        }

        @Bean
        CommentsRepository commentsRepository() {
            return mock(CommentsRepository.class);
        }

        @Bean
        ReportsRepository reportsRepository() {
            return mock(ReportsRepository.class);
        }

        @Bean
        PostAttachmentsRepository postAttachmentsRepository() {
            return mock(PostAttachmentsRepository.class);
        }

        @Bean
        FileAssetsRepository fileAssetsRepository() {
            return mock(FileAssetsRepository.class);
        }

        @Bean
        FileAssetExtractionsRepository fileAssetExtractionsRepository() {
            return mock(FileAssetExtractionsRepository.class);
        }

        @Bean
        WebContentFetchService webContentFetchService() {
            return mock(WebContentFetchService.class);
        }

        @Bean
        LlmGateway llmGateway() {
            LlmGateway gw = mock(LlmGateway.class);
            String raw = "{\"choices\":[{\"message\":{\"content\":\"{\\\"decision\\\":\\\"APPROVE\\\"}\"}}]}";
            when(gw.chatOnceRouted(
                    eq(LlmQueueTaskType.TEXT_MODERATION),
                    nullable(String.class),
                    nullable(String.class),
                    anyList(),
                    any(),
                    any(),
                    nullable(Integer.class),
                    nullable(List.class),
                    any(),
                    nullable(Integer.class),
                    nullable(Map.class)
            )).thenAnswer(inv -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return new LlmGateway.RoutedChatOnceResult(raw, "p1", "text-model", null);
            });
            return gw;
        }

        @Bean
        AuditLogWriter auditLogWriter() {
            return mock(AuditLogWriter.class);
        }

        @Bean
        AuditDiffBuilder auditDiffBuilder() {
            return mock(AuditDiffBuilder.class);
        }

        @Bean
        com.example.EnterpriseRagCommunity.repository.access.UsersRepository usersRepository() {
            return mock(com.example.EnterpriseRagCommunity.repository.access.UsersRepository.class);
        }

        @Bean
        TagsRepository tagsRepository() {
            return mock(TagsRepository.class);
        }

        @Bean
        AdminModerationLlmService adminModerationLlmService(
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
                com.example.EnterpriseRagCommunity.repository.access.UsersRepository usersRepository,
                TagsRepository tagsRepository,
                PromptsRepository promptsRepository,
                WebContentFetchService webContentFetchService,
                LlmGateway llmGateway,
                AuditLogWriter auditLogWriter,
                AuditDiffBuilder auditDiffBuilder
        ) {
            return AdminModerationLlmServiceTestFactory.newService(
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
        }
    }

    @Autowired
    private AdminModerationLlmService service;

    @Autowired
    private PlatformTransactionManager txm;

    @Test
    void llmCallShouldNotRunInsideActiveTransaction() {
        TransactionTemplate tt = new TransactionTemplate(txm);
        tt.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            LlmModerationTestRequest req = new LlmModerationTestRequest();
            req.setText("x");
            service.test(req);
            return null;
        });
    }
}

