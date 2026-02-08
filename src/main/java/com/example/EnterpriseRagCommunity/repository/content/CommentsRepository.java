package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommentsRepository extends JpaRepository<CommentsEntity, Long>, JpaSpecificationExecutor<CommentsEntity> {
    Page<CommentsEntity> findByPostIdAndStatusAndIsDeletedFalse(Long postId, CommentStatus status, Pageable pageable);

    List<CommentsEntity> findByIdInAndIsDeletedFalseAndStatus(List<Long> ids, CommentStatus status);

    @Query("select c from CommentsEntity c " +
            "where c.postId = :postId and c.isDeleted = false and (" +
            "c.status = com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus.VISIBLE " +
            "or (c.status = com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus.PENDING and c.authorId = :authorId)" +
            ")")
    Page<CommentsEntity> findVisibleOrMinePending(@Param("postId") Long postId, @Param("authorId") Long authorId, Pageable pageable);
    Page<CommentsEntity> findByAuthorIdAndIsDeletedFalse(Long authorId, Pageable pageable);
    Page<CommentsEntity> findByParentIdAndIsDeletedFalse(Long parentId, Pageable pageable);
    Page<CommentsEntity> findByIsDeletedFalseAndCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("select c from CommentsEntity c " +
            "where c.isDeleted = false and c.status = com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus.VISIBLE " +
            "and (:fromId is null or c.id > :fromId) " +
            "order by c.id asc")
    Page<CommentsEntity> scanVisibleFromId(@Param("fromId") Long fromId, Pageable pageable);

    long countByPostIdAndStatusAndIsDeletedFalse(Long postId, CommentStatus status);

    // Used by user hard-delete pre-checks (avoid FK constraint errors)
    long countByAuthorId(Long authorId);
}
