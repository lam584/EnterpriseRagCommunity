package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RagChatPostCommentAggregationServiceTest {

    @Test
    void aggregatesCommentHitAndAddsPostContentWhenOnlyCommentMatches() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        RagChatPostCommentAggregationService svc = new RagChatPostCommentAggregationService(postsRepository);

        RagCommentChatRetrievalService.Hit c = new RagCommentChatRetrievalService.Hit();
        c.setPostId(1L);
        c.setCommentId(10L);
        c.setChunkIndex(0);
        c.setScore(0.9);
        c.setContentText("这条评论里提到了一个非常具体的现象");

        PostsEntity p = new PostsEntity();
        p.setId(1L);
        p.setStatus(PostStatus.PUBLISHED);
        p.setTitle("示例帖子");
        p.setContent("这是帖子正文，用于补全评论上下文。");

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p));

        RagChatPostCommentAggregationService.Config cfg = new RagChatPostCommentAggregationService.Config();
        cfg.setIncludePostContentPolicy(RagChatPostCommentAggregationService.IncludePostContentPolicy.ON_COMMENT_HIT);
        cfg.setPerPostMaxCommentChunks(2);

        List<RagPostChatRetrievalService.Hit> out = svc.aggregate("query", List.of(), List.of(c), cfg);
        assertEquals(1, out.size());
        assertNotNull(out.get(0).getPostId());
        assertEquals(1L, out.get(0).getPostId());
        assertTrue(out.get(0).getContentText().contains("帖子正文："));
        assertTrue(out.get(0).getContentText().contains("命中评论片段"));
    }

    @Test
    void canDisablePostContentEvenIfCommentHits() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        RagChatPostCommentAggregationService svc = new RagChatPostCommentAggregationService(postsRepository);

        RagCommentChatRetrievalService.Hit c = new RagCommentChatRetrievalService.Hit();
        c.setPostId(1L);
        c.setCommentId(10L);
        c.setScore(0.9);
        c.setContentText("评论命中");

        PostsEntity p = new PostsEntity();
        p.setId(1L);
        p.setStatus(PostStatus.PUBLISHED);
        p.setTitle("示例帖子");
        p.setContent("帖子正文");

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p));

        RagChatPostCommentAggregationService.Config cfg = new RagChatPostCommentAggregationService.Config();
        cfg.setIncludePostContentPolicy(RagChatPostCommentAggregationService.IncludePostContentPolicy.NEVER);
        cfg.setPerPostMaxCommentChunks(1);

        List<RagPostChatRetrievalService.Hit> out = svc.aggregate("query", List.of(), List.of(c), cfg);
        assertEquals(1, out.size());
        assertTrue(out.get(0).getContentText().contains("命中评论片段"));
        assertTrue(!out.get(0).getContentText().contains("帖子正文："));
    }

    @Test
    void keepsFileAssetHitContentWhenChatAugmentIsEnabled() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        RagChatPostCommentAggregationService svc = new RagChatPostCommentAggregationService(postsRepository);

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of());

        RagPostChatRetrievalService.Hit fileHit = new RagPostChatRetrievalService.Hit();
        fileHit.setDocId("file_asset_12_chunk_0");
        fileHit.setSourceType("FILE_ASSET");
        fileHit.setFileAssetId(12L);
        fileHit.setPostId(1L);
        fileHit.setChunkIndex(0);
        fileHit.setScore(0.93);
        fileHit.setTitle("操作手册.pdf");
        fileHit.setContentText("这是文件抽取出的关键内容，用于回答用户问题。");

        RagChatPostCommentAggregationService.Config cfg = new RagChatPostCommentAggregationService.Config();
        cfg.setIncludePostContentPolicy(RagChatPostCommentAggregationService.IncludePostContentPolicy.ON_COMMENT_HIT);
        cfg.setPerPostMaxCommentChunks(2);

        List<RagPostChatRetrievalService.Hit> out = svc.aggregate("query", List.of(fileHit), List.of(), cfg);
        assertEquals(1, out.size());
        assertEquals("FILE_ASSET", out.get(0).getSourceType());
        assertEquals(12L, out.get(0).getFileAssetId());
        assertTrue(out.get(0).getContentText().contains("帖子正文："));
        assertTrue(out.get(0).getContentText().contains("这是文件抽取出的关键内容"));
    }
}

