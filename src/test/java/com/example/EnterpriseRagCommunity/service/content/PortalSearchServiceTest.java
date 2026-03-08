package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PortalSearchHitDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetChatRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortalSearchServiceTest {

    @Test
    void search_blankQuery_returnsEmptyPage_andSkipsDependencies() {
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        BoardAccessControlService boardAccessControlService = mock(BoardAccessControlService.class);

        PortalSearchService svc = new PortalSearchService(
                hybridRagRetrievalService,
                hybridRetrievalConfigService,
                ragCommentChatRetrievalService,
                ragFileAssetChatRetrievalService,
                postsRepository,
                commentsRepository,
                fileAssetsRepository,
                boardAccessControlService
        );

        Page<PortalSearchHitDTO> out = svc.search("   ", null, 1, 20);

        assertNotNull(out);
        assertEquals(0, out.getTotalElements());
        assertEquals(List.of(), out.getContent());

        verifyNoInteractions(hybridRagRetrievalService, hybridRetrievalConfigService, ragCommentChatRetrievalService, ragFileAssetChatRetrievalService);
        verifyNoInteractions(postsRepository, commentsRepository, fileAssetsRepository, boardAccessControlService);
    }

    @Test
    void search_mixedHits_paginates_andBuildsUrls() {
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        BoardAccessControlService boardAccessControlService = mock(BoardAccessControlService.class);

        PortalSearchService svc = new PortalSearchService(
                hybridRagRetrievalService,
                hybridRetrievalConfigService,
                ragCommentChatRetrievalService,
                ragFileAssetChatRetrievalService,
                postsRepository,
                commentsRepository,
                fileAssetsRepository,
                boardAccessControlService
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(cfg);
        when(hybridRetrievalConfigService.normalizeConfig(any(HybridRetrievalConfigDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        HybridRagRetrievalService.DocHit post1Hit = new HybridRagRetrievalService.DocHit();
        post1Hit.setPostId(1L);
        post1Hit.setFusedScore(10.0);
        post1Hit.setContentText("hit snippet");

        HybridRagRetrievalService.DocHit post2Missing = new HybridRagRetrievalService.DocHit();
        post2Missing.setPostId(2L);
        post2Missing.setFusedScore(9.0);

        HybridRagRetrievalService.RetrieveResult rr = new HybridRagRetrievalService.RetrieveResult();
        rr.setFinalHits(Arrays.asList(null, post1Hit, post2Missing, new HybridRagRetrievalService.DocHit()));
        when(hybridRagRetrievalService.retrieve(eq("hello"), eq(null), any(HybridRetrievalConfigDTO.class), anyBoolean()))
                .thenReturn(rr);

        RagCommentChatRetrievalService.Hit c1 = new RagCommentChatRetrievalService.Hit();
        c1.setCommentId(10L);
        c1.setPostId(1L);
        c1.setScore(0.2);
        c1.setContentText("");

        RagCommentChatRetrievalService.Hit cBad = new RagCommentChatRetrievalService.Hit();
        cBad.setCommentId(null);
        cBad.setPostId(1L);

        when(ragCommentChatRetrievalService.retrieve(eq("hello"), anyInt())).thenReturn(Arrays.asList(null, cBad, c1));

        RagFileAssetChatRetrievalService.Hit f1 = new RagFileAssetChatRetrievalService.Hit();
        f1.setFileAssetId(100L);
        f1.setScore(0.1);
        f1.setFileName("fallback.txt");
        f1.setPostIds(Arrays.asList(null, 2L, 1L));
        f1.setContentText("file content");
        when(ragFileAssetChatRetrievalService.retrieve(eq("hello"), anyInt())).thenReturn(List.of(f1));

        PostsEntity post1 = new PostsEntity();
        post1.setId(1L);
        post1.setTitle("t1");
        post1.setContent("c1");
        post1.setStatus(PostStatus.PUBLISHED);
        post1.setIsDeleted(false);
        post1.setCreatedAt(LocalDateTime.now().minusDays(10));
        post1.setPublishedAt(LocalDateTime.now().minusDays(5));
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(any(), eq(PostStatus.PUBLISHED))).thenReturn(List.of(post1));
        when(boardAccessControlService.currentUserRoleIds()).thenReturn(java.util.Set.of());
        when(boardAccessControlService.canViewBoard(anyLong(), any())).thenReturn(true);

        CommentsEntity comment1 = new CommentsEntity();
        comment1.setId(10L);
        comment1.setPostId(1L);
        comment1.setStatus(CommentStatus.VISIBLE);
        comment1.setIsDeleted(false);
        comment1.setCreatedAt(LocalDateTime.now().minusDays(1));
        comment1.setContent("comment content fallback");
        when(commentsRepository.findByIdInAndIsDeletedFalseAndStatus(any(), eq(CommentStatus.VISIBLE))).thenReturn(List.of(comment1));

        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(100L);
        fa.setCreatedAt(LocalDateTime.now().minusDays(2));
        fa.setOriginalName("   ");
        when(fileAssetsRepository.findAllById(any())).thenReturn(List.of(fa));

        Page<PortalSearchHitDTO> out = svc.search(" hello ", null, 1, 2);

        assertNotNull(out);
        assertEquals(2, out.getContent().size());
        assertEquals(3, out.getTotalElements());
        for (PortalSearchHitDTO x : out.getContent()) {
            assertNotNull(x.getType());
        }

        Page<PortalSearchHitDTO> emptyPage = svc.search(" hello ", null, 10, 2);
        assertNotNull(emptyPage);
        assertEquals(List.of(), emptyPage.getContent());
        assertEquals(3, emptyPage.getTotalElements());
    }

    @Test
    void search_filtersLowRelevanceAndInaccessibleBoardHits() {
        HybridRagRetrievalService hybridRagRetrievalService = mock(HybridRagRetrievalService.class);
        HybridRetrievalConfigService hybridRetrievalConfigService = mock(HybridRetrievalConfigService.class);
        RagCommentChatRetrievalService ragCommentChatRetrievalService = mock(RagCommentChatRetrievalService.class);
        RagFileAssetChatRetrievalService ragFileAssetChatRetrievalService = mock(RagFileAssetChatRetrievalService.class);
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        FileAssetsRepository fileAssetsRepository = mock(FileAssetsRepository.class);
        BoardAccessControlService boardAccessControlService = mock(BoardAccessControlService.class);

        PortalSearchService svc = new PortalSearchService(
                hybridRagRetrievalService,
                hybridRetrievalConfigService,
                ragCommentChatRetrievalService,
                ragFileAssetChatRetrievalService,
                postsRepository,
                commentsRepository,
                fileAssetsRepository,
                boardAccessControlService
        );

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(cfg);
        when(hybridRetrievalConfigService.normalizeConfig(any(HybridRetrievalConfigDTO.class))).thenAnswer(inv -> inv.getArgument(0));

        HybridRagRetrievalService.DocHit post1Hit = new HybridRagRetrievalService.DocHit();
        post1Hit.setPostId(1L);
        post1Hit.setFusedScore(3.0);
        post1Hit.setContentText("cpu 排障");
        HybridRagRetrievalService.RetrieveResult rr = new HybridRagRetrievalService.RetrieveResult();
        rr.setFinalHits(List.of(post1Hit));
        when(hybridRagRetrievalService.retrieve(eq("cpu"), eq(1L), any(HybridRetrievalConfigDTO.class), anyBoolean())).thenReturn(rr);

        RagCommentChatRetrievalService.Hit cGood = new RagCommentChatRetrievalService.Hit();
        cGood.setCommentId(11L);
        cGood.setPostId(1L);
        cGood.setScore(1.0);
        cGood.setContentText("完全无关键词");
        RagCommentChatRetrievalService.Hit cBadLow = new RagCommentChatRetrievalService.Hit();
        cBadLow.setCommentId(12L);
        cBadLow.setPostId(1L);
        cBadLow.setScore(0.3);
        cBadLow.setContentText("也无关键词");
        RagCommentChatRetrievalService.Hit cBadBoard = new RagCommentChatRetrievalService.Hit();
        cBadBoard.setCommentId(13L);
        cBadBoard.setPostId(2L);
        cBadBoard.setScore(0.95);
        cBadBoard.setContentText("cpu");
        when(ragCommentChatRetrievalService.retrieve(eq("cpu"), anyInt())).thenReturn(List.of(cGood, cBadLow, cBadBoard));

        when(ragFileAssetChatRetrievalService.retrieve(eq("cpu"), anyInt())).thenReturn(List.of());

        PostsEntity p1 = new PostsEntity();
        p1.setId(1L);
        p1.setBoardId(1L);
        p1.setTitle("CPU 运维");
        p1.setContent("cpu");
        p1.setStatus(PostStatus.PUBLISHED);
        p1.setIsDeleted(false);
        p1.setCreatedAt(LocalDateTime.now().minusDays(2));
        p1.setPublishedAt(LocalDateTime.now().minusDays(1));
        PostsEntity p2 = new PostsEntity();
        p2.setId(2L);
        p2.setBoardId(2L);
        p2.setTitle("其它版块");
        p2.setContent("cpu");
        p2.setStatus(PostStatus.PUBLISHED);
        p2.setIsDeleted(false);
        p2.setCreatedAt(LocalDateTime.now().minusDays(2));
        p2.setPublishedAt(LocalDateTime.now().minusDays(1));
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(any(), eq(PostStatus.PUBLISHED))).thenReturn(List.of(p1, p2));

        CommentsEntity cc1 = new CommentsEntity();
        cc1.setId(11L);
        cc1.setPostId(1L);
        cc1.setStatus(CommentStatus.VISIBLE);
        cc1.setIsDeleted(false);
        cc1.setContent("并不包含关键词");
        cc1.setCreatedAt(LocalDateTime.now().minusHours(3));
        CommentsEntity cc2 = new CommentsEntity();
        cc2.setId(12L);
        cc2.setPostId(1L);
        cc2.setStatus(CommentStatus.VISIBLE);
        cc2.setIsDeleted(false);
        cc2.setContent("并不包含关键词");
        cc2.setCreatedAt(LocalDateTime.now().minusHours(2));
        CommentsEntity cc3 = new CommentsEntity();
        cc3.setId(13L);
        cc3.setPostId(2L);
        cc3.setStatus(CommentStatus.VISIBLE);
        cc3.setIsDeleted(false);
        cc3.setContent("cpu");
        cc3.setCreatedAt(LocalDateTime.now().minusHours(1));
        when(commentsRepository.findByIdInAndIsDeletedFalseAndStatus(any(), eq(CommentStatus.VISIBLE))).thenReturn(List.of(cc1, cc2, cc3));

        when(boardAccessControlService.currentUserRoleIds()).thenReturn(Set.of(100L));
        when(boardAccessControlService.canViewBoard(eq(1L), any())).thenReturn(true);
        when(boardAccessControlService.canViewBoard(eq(2L), any())).thenReturn(false);

        Page<PortalSearchHitDTO> out = svc.search("cpu", 1L, 1, 20);

        assertNotNull(out);
        assertEquals(2, out.getContent().size());
        assertEquals(2, out.getTotalElements());
        List<String> types = out.getContent().stream().map(PortalSearchHitDTO::getType).toList();
        assertTrue(types.contains("POST"));
        assertTrue(types.contains("COMMENT"));
    }
}
