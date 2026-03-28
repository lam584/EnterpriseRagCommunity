package com.example.EnterpriseRagCommunity.service.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.CommentIndexSyncStatusDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.IndexSyncStatusDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.PostIndexSyncStatusDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagCommentsIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagFileAssetsIndexService;
import com.example.EnterpriseRagCommunity.service.retrieval.es.RagPostsIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminRetrievalIndexSyncStatusService {

    private final VectorIndicesRepository vectorIndicesRepository;
    private final PostsRepository postsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final CommentsRepository commentsRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final RagPostsIndexService ragPostsIndexService;
    private final RagCommentsIndexService ragCommentsIndexService;
    private final RagFileAssetsIndexService ragFileAssetsIndexService;

    private static void ok(IndexSyncStatusDTO dto, String status, String reason, String detail) {
        dto.setIndexed(true);
        dto.setStatus(status);
        dto.setReason(reason);
        dto.setDetail(detail);
    }

    private static void fail(IndexSyncStatusDTO dto, String status, String reason, String detail) {
        dto.setIndexed(false);
        dto.setStatus(status);
        dto.setReason(reason);
        dto.setDetail(detail);
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        LinkedHashSet<Long> uniq = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) uniq.add(id);
        }
        return new ArrayList<>(uniq);
    }

    public List<PostIndexSyncStatusDTO> batchPostStatuses(List<Long> ids) {
        List<Long> cleanIds = normalizeIds(ids);
        if (cleanIds.isEmpty()) return List.of();

        Map<Long, PostsEntity> postById = new HashMap<>();
        for (PostsEntity p : postsRepository.findAllById(cleanIds)) {
            if (p != null && p.getId() != null) postById.put(p.getId(), p);
        }

        String postIndexName = resolveIndexName("POST", ragPostsIndexService.defaultIndexName());
        String fileIndexName = resolveIndexName("FILE_ASSET", ragFileAssetsIndexService.defaultIndexName());

        List<PostIndexSyncStatusDTO> out = new ArrayList<>(cleanIds.size());
        for (Long postId : cleanIds) {
            PostsEntity post = postById.get(postId);

            IndexSyncStatusDTO postStatus = inspectPostDoc(post, postIndexName);
            IndexSyncStatusDTO attachmentStatus = inspectAttachmentDocs(post, fileIndexName);

            PostIndexSyncStatusDTO row = new PostIndexSyncStatusDTO();
            row.setPostId(postId);
            row.setPostIndex(postStatus);
            row.setAttachmentIndex(attachmentStatus);
            out.add(row);
        }
        return out;
    }

    public List<CommentIndexSyncStatusDTO> batchCommentStatuses(List<Long> ids) {
        List<Long> cleanIds = normalizeIds(ids);
        if (cleanIds.isEmpty()) return List.of();

        Map<Long, CommentsEntity> commentById = new HashMap<>();
        for (CommentsEntity c : commentsRepository.findAllById(cleanIds)) {
            if (c != null && c.getId() != null) commentById.put(c.getId(), c);
        }

        String commentIndexName = resolveIndexName("COMMENT", ragCommentsIndexService.defaultIndexName());

        List<CommentIndexSyncStatusDTO> out = new ArrayList<>(cleanIds.size());
        for (Long commentId : cleanIds) {
            CommentsEntity comment = commentById.get(commentId);
            IndexSyncStatusDTO status = inspectCommentDoc(comment, commentIndexName);

            CommentIndexSyncStatusDTO row = new CommentIndexSyncStatusDTO();
            row.setCommentId(commentId);
            row.setCommentIndex(status);
            out.add(row);
        }
        return out;
    }

    private IndexSyncStatusDTO inspectPostDoc(PostsEntity post, String indexName) {
        IndexSyncStatusDTO dto = new IndexSyncStatusDTO();
        dto.setIndexName(indexName);

        if (post == null || post.getId() == null) {
            fail(dto, "FAILED", "帖子不存在", "数据库中未查询到对应帖子记录");
            return dto;
        }
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            fail(dto, "FAILED", "帖子已删除", "已删除帖子不会同步到检索索引");
            return dto;
        }
        if (post.getStatus() != PostStatus.PUBLISHED) {
            fail(dto, "FAILED", "帖子未发布", "当前帖子状态为 " + post.getStatus() + "，仅已发布帖子参与检索索引");
            return dto;
        }
        if (!hasUsableIndex(indexName)) {
            fail(dto, "FAILED", "未找到帖子索引", "未配置 sourceType=POST 的可用向量索引，或索引不存在");
            return dto;
        }

        CountResult count = countByTerm(indexName, "post_id", post.getId());
        dto.setDocCount(count.count);
        if (count.error != null) {
            fail(dto, "FAILED", "查询帖子索引失败", count.error);
            return dto;
        }
        if (count.count > 0) {
            ok(dto, "SUCCESS", "已同步", null);
        } else {
            fail(dto, "FAILED", "帖子未入索引", "索引中 post_id=" + post.getId() + " 的文档数为 0；可能仍在增量窗口内，或同步失败");
        }
        return dto;
    }

    private IndexSyncStatusDTO inspectAttachmentDocs(PostsEntity post, String indexName) {
        IndexSyncStatusDTO dto = new IndexSyncStatusDTO();
        dto.setIndexName(indexName);

        if (post == null || post.getId() == null) {
            fail(dto, "FAILED", "帖子不存在", "数据库中未查询到对应帖子记录");
            return dto;
        }

        List<PostAttachmentsEntity> attachments = postAttachmentsRepository.findByPostId(post.getId());
        if (attachments == null || attachments.isEmpty()) {
            ok(dto, "NO_ATTACHMENT", "无附件", "该帖子没有附件，无需附件索引同步");
            dto.setIndexed(true);
            return dto;
        }
        if (!hasUsableIndex(indexName)) {
            fail(dto, "FAILED", "未找到附件索引", "未配置 sourceType=FILE_ASSET 的可用向量索引，或索引不存在");
            return dto;
        }

        CountResult count = countByTerm(indexName, "post_ids", post.getId());
        dto.setDocCount(count.count);
        if (count.error != null) {
            fail(dto, "FAILED", "查询附件索引失败", count.error);
            return dto;
        }
        if (count.count > 0) {
            ok(dto, "SUCCESS", "已同步", null);
        } else {
            fail(dto, "FAILED", "附件未入索引", "索引中 post_ids 包含 " + post.getId() + " 的文档数为 0；可能仍在增量窗口内，或同步失败");
        }
        return dto;
    }

    private IndexSyncStatusDTO inspectCommentDoc(CommentsEntity comment, String indexName) {
        IndexSyncStatusDTO dto = new IndexSyncStatusDTO();
        dto.setIndexName(indexName);

        if (comment == null || comment.getId() == null) {
            fail(dto, "FAILED", "评论不存在", "数据库中未查询到对应评论记录");
            return dto;
        }
        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            fail(dto, "FAILED", "评论已删除", "已删除评论不会同步到检索索引");
            return dto;
        }
        if (comment.getStatus() != CommentStatus.VISIBLE) {
            fail(dto, "FAILED", "评论不可见", "当前评论状态为 " + comment.getStatus() + "，仅可见评论参与检索索引");
            return dto;
        }
        if (!hasUsableIndex(indexName)) {
            fail(dto, "FAILED", "未找到评论索引", "未配置 sourceType=COMMENT 的可用向量索引，或索引不存在");
            return dto;
        }

        CountResult count = countByTerm(indexName, "comment_id", comment.getId());
        dto.setDocCount(count.count);
        if (count.error != null) {
            fail(dto, "FAILED", "查询评论索引失败", count.error);
            return dto;
        }
        if (count.count > 0) {
            ok(dto, "SUCCESS", "已同步", null);
        } else {
            fail(dto, "FAILED", "评论未入索引", "索引中 comment_id=" + comment.getId() + " 的文档数为 0；可能仍在增量窗口内，或同步失败");
        }
        return dto;
    }

    private String resolveIndexName(String sourceType, String fallback) {
        List<VectorIndicesEntity> all = vectorIndicesRepository.findAll();
        for (VectorIndicesEntity vi : all) {
            if (vi == null) continue;
            Map<String, Object> meta = vi.getMetadata();
            Object st = meta == null ? null : meta.get("sourceType");
            if (st == null) continue;
            if (!sourceType.equalsIgnoreCase(String.valueOf(st).trim())) continue;
            String cn = vi.getCollectionName();
            if (cn != null && !cn.isBlank()) return cn.trim();
            if (fallback != null && !fallback.isBlank()) return fallback.trim();
        }
        return fallback == null ? null : fallback.trim();
    }

    private boolean hasUsableIndex(String indexName) {
        if (indexName == null || indexName.isBlank()) return false;
        try {
            return elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).exists();
        } catch (Exception ignored) {
            return false;
        }
    }

    private CountResult countByTerm(String indexName, String field, Long value) {
        CountResult out = new CountResult();
        if (indexName == null || indexName.isBlank() || field == null || field.isBlank() || value == null) {
            out.error = "参数无效";
            return out;
        }
        try {
            CriteriaQuery q = new CriteriaQuery(new Criteria(field).is(value));
            q.setPageable(PageRequest.of(0, 1));
            SearchHits<Document> hits = elasticsearchOperations.search(q, Document.class, IndexCoordinates.of(indexName));
            out.count = hits == null ? 0L : hits.getTotalHits();
            return out;
        } catch (Exception ex) {
            out.error = ex.getMessage();
            out.count = 0L;
            return out;
        }
    }

    private static class CountResult {
        long count;
        String error;
    }
}
