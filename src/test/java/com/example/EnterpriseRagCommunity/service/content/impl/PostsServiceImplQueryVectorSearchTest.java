package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostsServiceImplQueryVectorSearchTest {

    @Test
    void queryPostIdReturnsSingletonOrEmpty() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PostsServiceImpl svc = newService(postsRepository, mock(HybridRagRetrievalService.class));

        PostsEntity p = new PostsEntity();
        p.setId(10L);
        when(postsRepository.findByIdAndIsDeletedFalse(10L)).thenReturn(Optional.of(p));

        Page<PostsEntity> r1 = svc.query(null, 10L, null, null, null, null, null, null, 1, 10, null, null);
        assertEquals(1, r1.getContent().size());
        assertEquals(10L, r1.getContent().get(0).getId());

        when(postsRepository.findByIdAndIsDeletedFalse(11L)).thenReturn(Optional.empty());
        Page<PostsEntity> r2 = svc.query(null, 11L, null, null, null, null, null, null, 1, 10, null, null);
        assertEquals(0, r2.getContent().size());
    }

    @Test
    void queryKeywordWithStatusNotPublishedReturnsEmptyWithoutCallingRetrieval() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        PostsServiceImpl svc = newService(postsRepository, hybridRagRetrievalService);

        Page<PostsEntity> r = svc.query("  k  ", null, null, null, PostStatus.DRAFT, null, null, null, 1, 10, "createdAt", "ASC");
        assertEquals(0, r.getContent().size());
        assertEquals(0, r.getTotalElements());
        verify(hybridRagRetrievalService, never()).retrieve(any(), any(), any(), anyBoolean());
    }

    @Test
    void queryKeywordHandlesNullRetrieveResultAndNullFinalHits() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        PostsServiceImpl svc = newService(postsRepository, hybridRagRetrievalService);

        when(hybridRagRetrievalService.retrieve(eq("k"), eq(null), any(), eq(false))).thenReturn(null);
        Page<PostsEntity> r1 = svc.query("k", null, null, null, PostStatus.PUBLISHED, null, null, null, 1, 10, "hotScore", "DESC");
        assertEquals(0, r1.getContent().size());
        assertEquals(0, r1.getTotalElements());

        HybridRagRetrievalService.RetrieveResult rr = new HybridRagRetrievalService.RetrieveResult();
        rr.setFinalHits(null);
        when(hybridRagRetrievalService.retrieve(eq("k2"), eq(null), any(), eq(false))).thenReturn(rr);
        Page<PostsEntity> r2 = svc.query("k2", null, null, null, null, null, null, null, 1, 10, "hot_score", "ASC");
        assertEquals(0, r2.getContent().size());
        assertEquals(0, r2.getTotalElements());
    }

    @Test
    void queryKeywordVectorSearchFiltersAndPagingBranches() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        PostsServiceImpl svc = newService(postsRepository, hybridRagRetrievalService);

        HybridRagRetrievalService.DocHit h0 = null;
        HybridRagRetrievalService.DocHit h1 = new HybridRagRetrievalService.DocHit();
        h1.setPostId(1L);
        HybridRagRetrievalService.DocHit h2 = new HybridRagRetrievalService.DocHit();
        h2.setPostId(1L);
        HybridRagRetrievalService.DocHit h3 = new HybridRagRetrievalService.DocHit();
        h3.setPostId(null);
        HybridRagRetrievalService.DocHit h4 = new HybridRagRetrievalService.DocHit();
        h4.setPostId(2L);

        HybridRagRetrievalService.RetrieveResult rr = new HybridRagRetrievalService.RetrieveResult();
        rr.setFinalHits(Arrays.asList(h0, h1, h2, h3, h4));
        when(hybridRagRetrievalService.retrieve(eq("k"), eq(99L), any(), eq(false))).thenReturn(rr);

        PostsEntity pNull = null;
        PostsEntity pIdNull = new PostsEntity();
        pIdNull.setId(null);
        PostsEntity p1 = new PostsEntity();
        p1.setId(1L);
        p1.setStatus(PostStatus.PUBLISHED);
        p1.setIsDeleted(false);
        p1.setAuthorId(2L);
        p1.setCreatedAt(LocalDateTime.of(2020, 1, 1, 0, 0));

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(eq(List.of(1L)), eq(PostStatus.PUBLISHED)))
                .thenReturn(Arrays.asList(pNull, pIdNull, p1));

        Page<PostsEntity> r1 = svc.query("k", null, null, 99L, PostStatus.PUBLISHED, null, null, null, 1, 1, "created_at", "ASC");
        assertEquals(1, r1.getContent().size());
        assertEquals(1L, r1.getContent().get(0).getId());
        assertEquals(2, r1.getTotalElements());

        Page<PostsEntity> r2 = svc.query("k", null, null, 99L, PostStatus.PUBLISHED, null, null, null, 3, 1, "updated_at", "DESC");
        assertEquals(0, r2.getContent().size());
        assertEquals(2, r2.getTotalElements());

        PostsEntity p2 = new PostsEntity();
        p2.setId(2L);
        p2.setStatus(PostStatus.PUBLISHED);
        p2.setIsDeleted(false);
        p2.setAuthorId(1L);
        p2.setCreatedAt(LocalDateTime.of(2022, 1, 2, 0, 0));

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(eq(List.of(1L, 2L)), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p1, p2));
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(eq(List.of(2L)), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p2));

        Page<PostsEntity> r3 = svc.query(
                "k",
                null,
                null,
                99L,
                PostStatus.PUBLISHED,
                1L,
                LocalDate.of(2022, 1, 2),
                LocalDate.of(2022, 1, 2),
                1,
                10,
                "published_at",
                "DESC"
        );
        assertNotNull(r3);
        assertEquals(1, r3.getContent().size());
        assertEquals(2L, r3.getContent().get(0).getId());
    }

    @Test
    void queryNonKeywordBuildsSpecAndDelegatesToFindAll() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        PostsServiceImpl svc = newService(postsRepository, mock(HybridRagRetrievalService.class));

        Page<PostsEntity> expected = new PageImpl<>(List.of());
        when(postsRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(expected);

        Page<PostsEntity> r = svc.query(
                null,
                null,
                null,
                1L,
                PostStatus.PUBLISHED,
                2L,
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 2),
                0,
                500,
                "unknownField",
                "ASC"
        );
        assertNotNull(r);
        verify(postsRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class));
    }

    private static PostsServiceImpl newService(PostsRepository postsRepository, HybridRagRetrievalService hybridRagRetrievalService) {
        return new PostsServiceImpl(
                postsRepository,
                mock(com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.AdministratorService.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner.class),
                mock(com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner.class),
                mock(com.example.EnterpriseRagCommunity.service.ai.AiPostSummaryTriggerService.class),
                mock(com.example.EnterpriseRagCommunity.repository.content.TagsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService.class),
                hybridRagRetrievalService,
                mock(com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService.class),
                mock(com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService.class),
                mock(com.example.EnterpriseRagCommunity.service.access.AuditLogWriter.class),
                mock(com.example.EnterpriseRagCommunity.service.content.PostComposeConfigService.class),
                mock(com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository.class)
        );
    }
}
