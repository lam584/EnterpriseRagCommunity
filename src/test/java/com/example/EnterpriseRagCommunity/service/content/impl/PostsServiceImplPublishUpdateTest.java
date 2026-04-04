package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeConfigDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.AiPostSummaryTriggerService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import com.example.EnterpriseRagCommunity.service.content.PostComposeConfigService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PostsServiceImplPublishUpdateTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishThrowsWhenDtoNull() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);
        AiPostSummaryTriggerService aiPostSummaryTriggerService = mock(AiPostSummaryTriggerService.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService = mock(RagPostIndexVisibilitySyncService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        BoardAccessControlService boardAccessControlService = mock(BoardAccessControlService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostComposeConfigService postComposeConfigService = mock(PostComposeConfigService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);

        PostsServiceImpl svc = newService(
                postsRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                administratorService,
                adminModerationQueueService,
                moderationRuleAutoRunner,
                moderationVecAutoRunner,
                moderationLlmAutoRunner,
                aiPostSummaryTriggerService,
                tagsRepository,
                ragPostIndexVisibilitySyncService,
                hybridRagRetrievalService,
                boardAccessControlService,
                auditLogWriter,
                postComposeConfigService,
                moderationQueueRepository
        );
        assertThrows(IllegalArgumentException.class, () -> svc.publish(null));
    }

    @Test
    void publishWritesAuditWhenNotLoggedIn() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);
        AiPostSummaryTriggerService aiPostSummaryTriggerService = mock(AiPostSummaryTriggerService.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService = mock(RagPostIndexVisibilitySyncService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        BoardAccessControlService boardAccessControlService = mock(BoardAccessControlService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostComposeConfigService postComposeConfigService = mock(PostComposeConfigService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);

        PostsServiceImpl svc = newService(
                postsRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                administratorService,
                adminModerationQueueService,
                moderationRuleAutoRunner,
                moderationVecAutoRunner,
                moderationLlmAutoRunner,
                aiPostSummaryTriggerService,
                tagsRepository,
                ragPostIndexVisibilitySyncService,
                hybridRagRetrievalService,
                boardAccessControlService,
                auditLogWriter,
                postComposeConfigService,
                moderationQueueRepository
        );

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(1L);
        dto.setContent("c");

        assertThrows(AuthenticationException.class, () -> svc.publish(dto));
        verify(auditLogWriter).write(any(), any(), eq("POST_PUBLISH"), eq("POST"), any(), any(), any(), any(), any());
        verifyNoInteractions(postsRepository);
    }

    @Test
    void publishFailsWhenBoardIdNull() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(null);
        dto.setTitle("t");
        dto.setContent("c");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("boardId 不能为空", ex.getMessage());
        verify(auditLogWriter).write(eq(10L), any(), eq("POST_PUBLISH"), eq("POST"), any(), eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.FAIL), any(), any(), eq(Map.of()));
    }

    @Test
    void publishFailsWhenNoPermission() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of(1L));
        when(getBoardAccessControlService(svc).canPostBoard(2L, Set.of(1L))).thenReturn(false);

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        assertThrows(AccessDeniedException.class, () -> svc.publish(dto));
        verify(auditLogWriter).write(eq(10L), any(), eq("POST_PUBLISH"), eq("POST"), any(), eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.FAIL), any(), any(), eq(Map.of("boardId", 2L)));
    }

    @Test
    void publishFailsWhenRequireTitleAndBlank() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(true);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(100);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> {
            PostsEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("   ");
        dto.setContent("c");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("标题不能为空", ex.getMessage());
    }

    @Test
    void publishFailsWhenTitleTooLong() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(100);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("x".repeat(192));
        dto.setContent("c");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("标题过长（最多 191 字符）", ex.getMessage());
    }

    @Test
    void publishFailsWhenContentTooLong() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("cc");
        dto.setContentFormat(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("内容过长（最多 1 字符）", ex.getMessage());
    }

    @Test
    void publishFailsWhenRequireTagsAndMissing() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(true);
        cfg.setMaxContentChars(100);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");
        dto.setTags(List.of(" ", "\t"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("标签不能为空", ex.getMessage());
    }

    @Test
    void publishChunkingBypassesAttachmentLimitAndSanitizesFileNames() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(1);
        cfg.setChunkThresholdChars(3);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> {
            PostsEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });

        FileAssetsEntity a1 = asset(1L, 10L, FileAssetStatus.READY, "http://host/path/name1.txt?x=1");
        FileAssetsEntity a2 = asset(2L, 10L, FileAssetStatus.READY, "http://host/path/name2.txt#frag");
        FileAssetsEntity a3 = asset(3L, 10L, FileAssetStatus.READY, "http://host/path/");
        FileAssetsEntity a4 = asset(4L, 10L, FileAssetStatus.READY, "http://host/path/" + "a".repeat(600));

        when(getFileAssetsRepository(svc).findById(1L)).thenReturn(Optional.of(a1));
        when(getFileAssetsRepository(svc).findById(2L)).thenReturn(Optional.of(a2));
        when(getFileAssetsRepository(svc).findById(3L)).thenReturn(Optional.of(a3));
        when(getFileAssetsRepository(svc).findById(4L)).thenReturn(Optional.of(a4));

        doNothing().when(getAdminModerationQueueService(svc)).ensureEnqueuedPost(100L);
        doNothing().when(getAiPostSummaryTriggerService(svc)).scheduleGenerateAfterCommit(anyLong(), anyLong());
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle(" t ");
        dto.setContent("0123456789");
        dto.setContentFormat(null);
        dto.setAttachmentIds(Arrays.asList(1L, null, 2L, 3L, 4L, 1L));

        PostsEntity saved = svc.publish(dto);
        assertNotNull(saved);

        var captor = org.mockito.ArgumentCaptor.forClass(PostsEntity.class);
        verify(postsRepository).save(captor.capture());
        PostsEntity toSave = captor.getValue();
        assertEquals(true, toSave.getIsChunkedReview());
        assertEquals(Integer.valueOf(3), toSave.getChunkThresholdChars());
        assertEquals("CHARS", toSave.getChunkingStrategy());
        assertEquals(ContentFormat.MARKDOWN, toSave.getContentFormat());

        LinkedHashSet<Long> uniq = new LinkedHashSet<>(dto.getAttachmentIds());
        int expectedSaves = (int) uniq.stream().filter(x -> x != null).count();
        verify(getPostAttachmentsRepository(svc), org.mockito.Mockito.times(expectedSaves)).save(any());
    }

    @Test
    void publishThrowsWhenAttachmentLimitExceededWithoutBypass() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(1);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(false);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> {
            PostsEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");
        dto.setAttachmentIds(List.of(1L, 2L));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("附件数量超限（最多 1 个）", ex.getMessage());
        verify(getFileAssetsRepository(svc), never()).findById(anyLong());
    }

    @Test
    void publishCoversAttachmentOwnershipAndStatusBranches() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> {
            PostsEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        FileAssetsEntity noOwner = new FileAssetsEntity();
        noOwner.setId(1L);
        noOwner.setOwner(null);
        noOwner.setUrl("http://host/a.txt");
        noOwner.setStatus(FileAssetStatus.READY);

        when(getFileAssetsRepository(svc).findById(1L)).thenReturn(Optional.of(noOwner));

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");
        dto.setAttachmentIds(List.of(1L));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("无权使用该附件: 1", ex.getMessage());

        FileAssetsEntity badStatus = asset(2L, 10L, FileAssetStatus.UPLOADING, "http://host/b.txt");
        when(getFileAssetsRepository(svc).findById(2L)).thenReturn(Optional.of(badStatus));
        dto.setAttachmentIds(List.of(2L));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> svc.publish(dto));
        assertEquals("附件状态不可用: 2", ex2.getMessage());
    }

    @Test
    void publishRunsModerationImmediatelyWhenNoSynchronizationActive() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> {
            PostsEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });

        doNothing().when(getAdminModerationQueueService(svc)).ensureEnqueuedPost(100L);
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());
        doThrow(new RuntimeException("boom")).when(getAiPostSummaryTriggerService(svc)).scheduleGenerateAfterCommit(anyLong(), anyLong());
        doThrow(new RuntimeException("ignore")).when(getModerationRuleAutoRunner(svc)).runOnce();

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        PostsEntity saved = svc.publish(dto);
        assertNotNull(saved);
        verify(getModerationRuleAutoRunner(svc)).runOnce();
        verify(getModerationVecAutoRunner(svc)).runOnce();
        verify(getModerationLlmAutoRunner(svc)).runOnce();
    }

    @Test
    void publishRegistersModerationAfterCommitWhenSynchronizationActive() {
        TransactionSynchronizationManager.initSynchronization();
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));
        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        when(postsRepository.save(any())).thenAnswer(inv -> {
            PostsEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });

        doNothing().when(getAdminModerationQueueService(svc)).ensureEnqueuedPost(100L);
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsPublishDTO dto = new PostsPublishDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        svc.publish(dto);
        verify(getModerationRuleAutoRunner(svc), never()).runOnce();
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.get(0).afterCommit();
        verify(getModerationRuleAutoRunner(svc)).runOnce();
        verify(getModerationVecAutoRunner(svc)).runOnce();
        verify(getModerationLlmAutoRunner(svc)).runOnce();
    }

    @Test
    void updateValidatesAuthorAndBoardAndUpdatesAttachments() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity existing = new PostsEntity();
        existing.setId(200L);
        existing.setAuthorId(10L);
        existing.setIsDeleted(false);
        existing.setStatus(PostStatus.PUBLISHED);
        existing.setPublishedAt(LocalDateTime.now());
        when(postsRepository.findById(200L)).thenReturn(Optional.of(existing));
        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(3);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        FileAssetsEntity a1 = asset(1L, 10L, FileAssetStatus.READY, "http://host/path/name1.txt");
        when(getFileAssetsRepository(svc).findById(1L)).thenReturn(Optional.of(a1));

        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsUpdateDTO dto = new PostsUpdateDTO();
        dto.setBoardId(2L);
        dto.setTitle(null);
        dto.setContent("0123456789");
        dto.setContentFormat(null);
        dto.setAttachmentIds(Arrays.asList(1L, null, 1L));
        dto.setMetadata(Map.of("tags", List.of(" a ")));

        PostsEntity saved = svc.update(200L, dto);
        assertNotNull(saved);
        verify(getPostAttachmentsRepository(svc)).deleteByPostId(200L);
        verify(getPostAttachmentsRepository(svc)).save(any());
        verify(getRagPostIndexVisibilitySyncService(svc)).scheduleSyncAfterCommit(200L);
    }

    @Test
    void updateFailsWhenNotAuthorOrBoardIdNull() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity existing = new PostsEntity();
        existing.setId(200L);
        existing.setAuthorId(11L);
        existing.setIsDeleted(false);
        when(postsRepository.findById(200L)).thenReturn(Optional.of(existing));
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsUpdateDTO dto = new PostsUpdateDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto));
        assertEquals("无权编辑该帖子", ex.getMessage());

        existing.setAuthorId(10L);
        dto.setBoardId(null);

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto));
        assertEquals("boardId 不能为空", ex2.getMessage());
    }

    @Test
    void updateValidatesIdAndDtoAndDeletedAndPermission() {
        PostsServiceImpl svc0 = baseService(mock(PostsRepository.class), mock(AuditLogWriter.class));
        assertThrows(IllegalArgumentException.class, () -> svc0.update(null, new PostsUpdateDTO()));
        assertThrows(IllegalArgumentException.class, () -> svc0.update(1L, null));

        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity deleted = new PostsEntity();
        deleted.setId(9L);
        deleted.setAuthorId(10L);
        deleted.setIsDeleted(true);
        when(postsRepository.findById(9L)).thenReturn(Optional.of(deleted));
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsUpdateDTO dto = new PostsUpdateDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.update(9L, dto));
        assertEquals("帖子已删除: 9", ex.getMessage());

        PostsEntity ok = new PostsEntity();
        ok.setId(10L);
        ok.setAuthorId(10L);
        ok.setIsDeleted(false);
        when(postsRepository.findById(10L)).thenReturn(Optional.of(ok));

        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> svc.update(10L, dto));
    }

    @Test
    void updateCoversTitleContentTagsAndAttachmentErrorBranches() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity existing = new PostsEntity();
        existing.setId(200L);
        existing.setAuthorId(10L);
        existing.setIsDeleted(false);
        existing.setStatus(PostStatus.PENDING);
        when(postsRepository.findById(200L)).thenReturn(Optional.of(existing));
        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(true);
        cfg.setRequireTags(true);
        cfg.setMaxContentChars(1);
        cfg.setMaxAttachments(1);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(false);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);
        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsUpdateDTO dto1 = new PostsUpdateDTO();
        dto1.setBoardId(2L);
        dto1.setTitle(" ");
        dto1.setContent("c");
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto1));

        PostsUpdateDTO dto2 = new PostsUpdateDTO();
        dto2.setBoardId(2L);
        dto2.setTitle("x".repeat(192));
        dto2.setContent("c");
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto2));

        PostsUpdateDTO dto3 = new PostsUpdateDTO();
        dto3.setBoardId(2L);
        dto3.setTitle("t");
        dto3.setContent("cc");
        dto3.setContentFormat(null);
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto3));

        PostsUpdateDTO dto4 = new PostsUpdateDTO();
        dto4.setBoardId(2L);
        dto4.setTitle("t");
        dto4.setContent("c");
        dto4.setTags(List.of(" ", "\t"));
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto4));

        PostsUpdateDTO dto5 = new PostsUpdateDTO();
        dto5.setBoardId(2L);
        dto5.setTitle("t");
        dto5.setContent("c");
        dto5.setAttachmentIds(List.of(1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto5));

        FileAssetsEntity noOwner = new FileAssetsEntity();
        noOwner.setId(1L);
        noOwner.setOwner(null);
        noOwner.setUrl("http://host/a.txt");
        noOwner.setStatus(FileAssetStatus.READY);
        when(getFileAssetsRepository(svc).findById(1L)).thenReturn(Optional.of(noOwner));

        PostComposeConfigDTO cfg2 = new PostComposeConfigDTO();
        cfg2.setRequireTitle(false);
        cfg2.setRequireTags(false);
        cfg2.setMaxContentChars(1000);
        cfg2.setMaxAttachments(10);
        cfg2.setChunkThresholdChars(null);
        cfg2.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg2);

        PostsUpdateDTO dto6 = new PostsUpdateDTO();
        dto6.setBoardId(2L);
        dto6.setTitle("t");
        dto6.setContent("c");
        dto6.setAttachmentIds(List.of(1L));
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto6));

        FileAssetsEntity badStatus = asset(2L, 10L, FileAssetStatus.UPLOADING, "http://host/b.txt");
        when(getFileAssetsRepository(svc).findById(2L)).thenReturn(Optional.of(badStatus));

        dto6.setAttachmentIds(List.of(2L));
        assertThrows(IllegalArgumentException.class, () -> svc.update(200L, dto6));
    }

    @Test
    void updateDoesNotScheduleIndexSyncWhenNotPublishedOrDeleted() {
        SecurityContextTestSupport.setAuthenticatedEmail("alice@example.com");

        PostsRepository postsRepository = mock(PostsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        PostsServiceImpl svc = baseService(postsRepository, auditLogWriter);

        UsersEntity me = new UsersEntity();
        me.setId(10L);
        when(getAdministratorService(svc).findByUsername("alice@example.com")).thenReturn(Optional.of(me));

        PostsEntity existing = new PostsEntity();
        existing.setId(300L);
        existing.setAuthorId(10L);
        existing.setIsDeleted(false);
        existing.setStatus(PostStatus.PENDING);
        when(postsRepository.findById(300L)).thenReturn(Optional.of(existing));
        when(postsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(getBoardAccessControlService(svc).currentUserRoleIds()).thenReturn(Set.of());
        when(getBoardAccessControlService(svc).canPostBoard(eq(2L), any())).thenReturn(true);

        PostComposeConfigDTO cfg = new PostComposeConfigDTO();
        cfg.setRequireTitle(false);
        cfg.setRequireTags(false);
        cfg.setMaxContentChars(1000);
        cfg.setMaxAttachments(10);
        cfg.setChunkThresholdChars(null);
        cfg.setBypassAttachmentLimitWhenChunked(true);
        when(getPostComposeConfigService(svc).getConfig()).thenReturn(cfg);

        doNothing().when(getAuditLogWriter(svc)).write(any(), any(), any(), any(), any(), any(), any(), any(), any());

        PostsUpdateDTO dto = new PostsUpdateDTO();
        dto.setBoardId(2L);
        dto.setTitle("t");
        dto.setContent("c");

        svc.update(300L, dto);
        verify(getRagPostIndexVisibilitySyncService(svc), never()).scheduleSyncAfterCommit(anyLong());

        existing.setStatus(PostStatus.PUBLISHED);
        existing.setIsDeleted(true);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.update(300L, dto));
        assertEquals("帖子已删除: 300", ex.getMessage());
        verify(getRagPostIndexVisibilitySyncService(svc), never()).scheduleSyncAfterCommit(anyLong());
    }

    private static PostsServiceImpl baseService(PostsRepository postsRepository, AuditLogWriter auditLogWriter) {
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        AdminModerationQueueService adminModerationQueueService = mock(AdminModerationQueueService.class);
        ModerationRuleAutoRunner moderationRuleAutoRunner = mock(ModerationRuleAutoRunner.class);
        ModerationVecAutoRunner moderationVecAutoRunner = mock(ModerationVecAutoRunner.class);
        ModerationLlmAutoRunner moderationLlmAutoRunner = mock(ModerationLlmAutoRunner.class);
        AiPostSummaryTriggerService aiPostSummaryTriggerService = mock(AiPostSummaryTriggerService.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService = mock(RagPostIndexVisibilitySyncService.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        BoardAccessControlService boardAccessControlService = mock(BoardAccessControlService.class);
        PostComposeConfigService postComposeConfigService = mock(PostComposeConfigService.class);
        ModerationQueueRepository moderationQueueRepository = mock(ModerationQueueRepository.class);

        return newService(
                postsRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                administratorService,
                adminModerationQueueService,
                moderationRuleAutoRunner,
                moderationVecAutoRunner,
                moderationLlmAutoRunner,
                aiPostSummaryTriggerService,
                tagsRepository,
                ragPostIndexVisibilitySyncService,
                hybridRagRetrievalService,
                boardAccessControlService,
                auditLogWriter,
                postComposeConfigService,
                moderationQueueRepository
        );
    }

    private static PostsServiceImpl newService(PostsRepository postsRepository,
                                              PostAttachmentsRepository postAttachmentsRepository,
                                              FileAssetsRepository fileAssetsRepository,
                                              AdministratorService administratorService,
                                              AdminModerationQueueService adminModerationQueueService,
                                              ModerationRuleAutoRunner moderationRuleAutoRunner,
                                              ModerationVecAutoRunner moderationVecAutoRunner,
                                              ModerationLlmAutoRunner moderationLlmAutoRunner,
                                              AiPostSummaryTriggerService aiPostSummaryTriggerService,
                                              TagsRepository tagsRepository,
                                              RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService,
                                              HybridRagRetrievalService hybridRagRetrievalService,
                                              BoardAccessControlService boardAccessControlService,
                                              AuditLogWriter auditLogWriter,
                                              PostComposeConfigService postComposeConfigService,
                                              ModerationQueueRepository moderationQueueRepository) {
        VectorIndicesRepository vectorIndicesRepository = mock(VectorIndicesRepository.class);
        when(vectorIndicesRepository.findByStatus(any())).thenReturn(List.of());
        return new PostsServiceImpl(
                postsRepository,
                postAttachmentsRepository,
                fileAssetsRepository,
                administratorService,
                adminModerationQueueService,
                moderationRuleAutoRunner,
                moderationVecAutoRunner,
                moderationLlmAutoRunner,
                aiPostSummaryTriggerService,
                tagsRepository,
                ragPostIndexVisibilitySyncService,
                hybridRagRetrievalService,
                vectorIndicesRepository,
                mock(RagFileAssetIndexAsyncService.class),
                boardAccessControlService,
                auditLogWriter,
                postComposeConfigService,
                moderationQueueRepository
        );
    }

    private static FileAssetsEntity asset(Long id, Long ownerId, FileAssetStatus status, String url) {
        UsersEntity owner = new UsersEntity();
        owner.setId(ownerId);
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(id);
        fa.setOwner(owner);
        fa.setStatus(status);
        fa.setUrl(url);
        fa.setPath("/tmp/" + id);
        fa.setMimeType("text/plain");
        fa.setSha256("x");
        fa.setSizeBytes(1L);
        return fa;
    }

    private static AdministratorService getAdministratorService(PostsServiceImpl svc) {
        return (AdministratorService) ReflectionTestUtils.getField(svc, "administratorService");
    }

    private static BoardAccessControlService getBoardAccessControlService(PostsServiceImpl svc) {
        return (BoardAccessControlService) ReflectionTestUtils.getField(svc, "boardAccessControlService");
    }

    private static PostComposeConfigService getPostComposeConfigService(PostsServiceImpl svc) {
        return (PostComposeConfigService) ReflectionTestUtils.getField(svc, "postComposeConfigService");
    }

    private static FileAssetsRepository getFileAssetsRepository(PostsServiceImpl svc) {
        return (FileAssetsRepository) ReflectionTestUtils.getField(svc, "fileAssetsRepository");
    }

    private static PostAttachmentsRepository getPostAttachmentsRepository(PostsServiceImpl svc) {
        return (PostAttachmentsRepository) ReflectionTestUtils.getField(svc, "postAttachmentsRepository");
    }

    private static AdminModerationQueueService getAdminModerationQueueService(PostsServiceImpl svc) {
        return (AdminModerationQueueService) ReflectionTestUtils.getField(svc, "adminModerationQueueService");
    }

    private static ModerationRuleAutoRunner getModerationRuleAutoRunner(PostsServiceImpl svc) {
        return (ModerationRuleAutoRunner) ReflectionTestUtils.getField(svc, "moderationRuleAutoRunner");
    }

    private static ModerationVecAutoRunner getModerationVecAutoRunner(PostsServiceImpl svc) {
        return (ModerationVecAutoRunner) ReflectionTestUtils.getField(svc, "moderationVecAutoRunner");
    }

    private static ModerationLlmAutoRunner getModerationLlmAutoRunner(PostsServiceImpl svc) {
        return (ModerationLlmAutoRunner) ReflectionTestUtils.getField(svc, "moderationLlmAutoRunner");
    }

    private static AiPostSummaryTriggerService getAiPostSummaryTriggerService(PostsServiceImpl svc) {
        return (AiPostSummaryTriggerService) ReflectionTestUtils.getField(svc, "aiPostSummaryTriggerService");
    }

    private static AuditLogWriter getAuditLogWriter(PostsServiceImpl svc) {
        return (AuditLogWriter) ReflectionTestUtils.getField(svc, "auditLogWriter");
    }

    private static RagPostIndexVisibilitySyncService getRagPostIndexVisibilitySyncService(PostsServiceImpl svc) {
        return (RagPostIndexVisibilitySyncService) ReflectionTestUtils.getField(svc, "ragPostIndexVisibilitySyncService");
    }
}
