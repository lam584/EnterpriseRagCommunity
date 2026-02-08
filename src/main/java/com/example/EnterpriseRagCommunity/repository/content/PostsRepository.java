package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
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
public interface PostsRepository extends JpaRepository<PostsEntity, Long>, JpaSpecificationExecutor<PostsEntity> {
    // Soft-delete must be filtered explicitly in methods
    Page<PostsEntity> findByBoardIdAndStatusAndIsDeletedFalse(Long boardId, PostStatus status, Pageable pageable);
    Page<PostsEntity> findByAuthorIdAndIsDeletedFalse(Long authorId, Pageable pageable);
    Page<PostsEntity> findByIsDeletedFalseAndCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    List<PostsEntity> findTop50ByIsDeletedFalseAndStatusOrderByCreatedAtDesc(PostStatus status);

    // Time-based publication filtering
    Page<PostsEntity> findByPublishedAtBetweenAndIsDeletedFalse(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Status-only filtering with pagination
    Page<PostsEntity> findByStatusAndIsDeletedFalse(PostStatus status, Pageable pageable);


    @Query("select p.id from PostsEntity p where p.isDeleted = false and p.status = :status")
    List<Long> findIdsByStatusAndIsDeletedFalse(@Param("status") PostStatus status);

    @Query("select p from PostsEntity p where p.isDeleted = false and p.status = :status and (:boardId is null or p.boardId = :boardId) and (:fromId is null or p.id >= :fromId) order by p.id asc")
    Page<PostsEntity> scanByStatusAndBoardFromId(@Param("status") PostStatus status,
                                                 @Param("boardId") Long boardId,
                                                 @Param("fromId") Long fromId,
                                                 Pageable pageable);

    java.util.Optional<PostsEntity> findByIdAndIsDeletedFalse(Long id);
    List<PostsEntity> findByIdInAndIsDeletedFalseAndStatus(List<Long> ids, PostStatus status);

    // Used by user hard-delete pre-checks (avoid FK constraint errors)
    long countByAuthorId(Long authorId);
}
