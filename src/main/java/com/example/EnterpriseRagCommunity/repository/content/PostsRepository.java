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

    // Full-text search on title/content using MySQL MATCH ... AGAINST
    @Query(value = "SELECT * FROM posts WHERE is_deleted = 0 AND MATCH(title, content) AGAINST(:q IN BOOLEAN MODE)",
           countQuery = "SELECT COUNT(*) FROM posts WHERE is_deleted = 0 AND MATCH(title, content) AGAINST(:q IN BOOLEAN MODE)",
           nativeQuery = true)
    Page<PostsEntity> searchFullText(@Param("q") String query, Pageable pageable);

    // Time-based publication filtering
    Page<PostsEntity> findByPublishedAtBetweenAndIsDeletedFalse(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Status-only filtering with pagination
    Page<PostsEntity> findByStatusAndIsDeletedFalse(PostStatus status, Pageable pageable);
}
