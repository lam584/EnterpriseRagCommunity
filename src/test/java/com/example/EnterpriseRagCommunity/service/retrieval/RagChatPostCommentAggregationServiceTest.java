package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RagChatPostCommentAggregationServiceTest {

    private static RagChatPostCommentAggregationService newService(
            PostsRepository postsRepository,
            CommentsRepository commentsRepository,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository
    ) {
        return new RagChatPostCommentAggregationService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );
    }

    @Test
    void aggregatesCommentHitAndAddsPostContentWhenOnlyCommentMatches() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        RagChatPostCommentAggregationService svc = newService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );

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
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        RagChatPostCommentAggregationService svc = newService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );

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
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        RagChatPostCommentAggregationService svc = newService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );

        PostsEntity p = new PostsEntity();
        p.setId(1L);
        p.setStatus(PostStatus.PUBLISHED);
        p.setTitle("文件命中帖子");
        p.setContent("这是帖子正文，用于与附件片段一起提交给LLM。");
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(p));

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

    @Test
    void keepsPostHitContentWhenOnlyPostMatchesUnderOnCommentHitPolicy() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        RagChatPostCommentAggregationService svc = newService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of());

        RagPostChatRetrievalService.Hit postHit = new RagPostChatRetrievalService.Hit();
        postHit.setDocId("post_100_chunk_0");
        postHit.setPostId(100L);
        postHit.setChunkIndex(0);
        postHit.setScore(0.82);
        postHit.setTitle("仅帖子正文命中");
        postHit.setContentText("这是帖子正文中的关键事实，用于回答测试问题。");

        RagChatPostCommentAggregationService.Config cfg = new RagChatPostCommentAggregationService.Config();
        cfg.setIncludePostContentPolicy(RagChatPostCommentAggregationService.IncludePostContentPolicy.ON_COMMENT_HIT);
        cfg.setPerPostMaxCommentChunks(2);

        List<RagPostChatRetrievalService.Hit> out = svc.aggregate("query", List.of(postHit), List.of(), cfg);
        assertEquals(1, out.size());
        assertEquals(100L, out.get(0).getPostId());
        assertTrue(out.get(0).getContentText().contains("帖子正文："));
        assertTrue(out.get(0).getContentText().contains("关键事实"));
    }

    @Test
    void fallsBackToRawPostHitWhenPolicyDropsAllAggregatedContent() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        RagChatPostCommentAggregationService svc = newService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );

        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of());

        RagPostChatRetrievalService.Hit postHit = new RagPostChatRetrievalService.Hit();
        postHit.setDocId("post_200_chunk_0");
        postHit.setPostId(200L);
        postHit.setChunkIndex(0);
        postHit.setScore(0.7);
        postHit.setTitle("仅帖子命中-回退保护");
        postHit.setContentText("这是原始帖子命中的分片内容。\n用于验证聚合为空时的回退保护。");

        RagChatPostCommentAggregationService.Config cfg = new RagChatPostCommentAggregationService.Config();
        cfg.setIncludePostContentPolicy(RagChatPostCommentAggregationService.IncludePostContentPolicy.NEVER);
        cfg.setPerPostMaxCommentChunks(0);

        List<RagPostChatRetrievalService.Hit> out = svc.aggregate("query", List.of(postHit), List.of(), cfg);
        assertEquals(1, out.size());
        assertEquals(200L, out.get(0).getPostId());
        assertEquals("post_200_chunk_0", out.get(0).getDocId());
        assertTrue(out.get(0).getContentText().contains("回退保护"));
    }

    @Test
    void enrichesPostCommentAndAttachmentContextWhenOnlyOneSourceHits() {
        PostsRepository postsRepository = mock(PostsRepository.class);
        CommentsRepository commentsRepository = mock(CommentsRepository.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        RagChatPostCommentAggregationService svc = newService(
                postsRepository,
                commentsRepository,
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );

        PostsEntity post = new PostsEntity();
        post.setId(1L);
        post.setStatus(PostStatus.PUBLISHED);
        post.setTitle("综合上下文帖子");
        post.setContent("这是帖子正文，描述了业务背景和关键约束。");
        when(postsRepository.findByIdInAndIsDeletedFalseAndStatus(anyList(), eq(PostStatus.PUBLISHED)))
                .thenReturn(List.of(post));

        CommentsEntity comment = new CommentsEntity();
        comment.setId(11L);
        comment.setPostId(1L);
        comment.setAuthorId(2L);
        comment.setStatus(CommentStatus.VISIBLE);
        comment.setIsDeleted(false);
        comment.setContent("这是评论里的补充事实，应该提供给LLM。\n它用于验证评论引用可用。");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        when(commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(eq(1L), eq(CommentStatus.VISIBLE), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(comment)));

        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setId(100L);
        att.setPostId(1L);
        att.setFileAssetId(101L);
        att.setCreatedAt(LocalDateTime.now());
        when(postAttachmentsRepository.findByPostId(1L)).thenReturn(List.of(att));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(101L);
        ex.setExtractedText("这是附件抽取文本中的关键条款，应该作为附件上下文提交给LLM。");
        when(fileAssetExtractionsRepository.findAllById(anyList())).thenReturn(List.of(ex));

        RagPostChatRetrievalService.Hit singleFileHit = new RagPostChatRetrievalService.Hit();
        singleFileHit.setDocId("file_asset_101_chunk_0");
        singleFileHit.setPostId(1L);
        singleFileHit.setFileAssetId(101L);
        singleFileHit.setSourceType("FILE_ASSET");
        singleFileHit.setChunkIndex(0);
        singleFileHit.setScore(0.91);
        singleFileHit.setTitle("附件命中");
        singleFileHit.setContentText("附件命中片段：参数阈值为 0.8，需人工复核。");

        RagChatPostCommentAggregationService.Config cfg = new RagChatPostCommentAggregationService.Config();
        cfg.setIncludePostContentPolicy(RagChatPostCommentAggregationService.IncludePostContentPolicy.ON_COMMENT_HIT);
        cfg.setPerPostMaxCommentChunks(2);

        List<RagPostChatRetrievalService.Hit> out = svc.aggregate("query", List.of(singleFileHit), List.of(), cfg);
        assertEquals(1, out.size());
        String ctx = out.get(0).getContentText();
        assertTrue(ctx.contains("帖子正文："));
        assertTrue(ctx.contains("命中评论片段："));
        assertTrue(ctx.contains("关联附件片段："));
        assertTrue(ctx.contains("补充事实"));
        assertTrue(ctx.contains("参数阈值") || ctx.contains("关键条款"));
    }
}

