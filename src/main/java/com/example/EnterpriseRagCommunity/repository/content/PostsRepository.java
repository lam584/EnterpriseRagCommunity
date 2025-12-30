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

    /**
     * Full-text search with a stable DB column sort.
     *
     * NOTE: When using nativeQuery, Spring Data may append Pageable sort properties directly into SQL.
     * If callers pass entity property names (e.g. createdAt), it can cause "Unknown column".
     * This method safeguards by using explicit db column ordering.
     */
    @Query(value = "SELECT * FROM posts WHERE is_deleted = 0 AND MATCH(title, content) AGAINST(:q IN BOOLEAN MODE) ORDER BY created_at DESC",
           countQuery = "SELECT COUNT(*) FROM posts WHERE is_deleted = 0 AND MATCH(title, content) AGAINST(:q IN BOOLEAN MODE)",
           nativeQuery = true)
    Page<PostsEntity> searchFullTextOrderByCreatedAtDesc(@Param("q") String query, Pageable pageable);

    // Time-based publication filtering
    Page<PostsEntity> findByPublishedAtBetweenAndIsDeletedFalse(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Status-only filtering with pagination
    Page<PostsEntity> findByStatusAndIsDeletedFalse(PostStatus status, Pageable pageable);

    /**
     * Fuzzy search (LIKE) on title/content.
     *
     * Note: Uses ESCAPE '\\' so callers can safely pass escaped %/_.
     * We keep ORDER BY as DB column to avoid Pageable sort injection/unknown column issues for nativeQuery.
     */
    @Query(value = "SELECT * FROM posts " +
            "WHERE is_deleted = 0 AND (title LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\' OR content LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\') " +
            "ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM posts " +
                    "WHERE is_deleted = 0 AND (title LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\' OR content LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\')",
            nativeQuery = true)
    Page<PostsEntity> searchLikeOrderByCreatedAtDesc(@Param("kw") String keyword, Pageable pageable);

    // --- Keyword search with optional status filter (admin needs to see PENDING etc.) ---

    @Query(value = "SELECT * FROM posts WHERE is_deleted = 0 AND (:status IS NULL OR status = :status) AND MATCH(title, content) AGAINST(:q IN BOOLEAN MODE) ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM posts WHERE is_deleted = 0 AND (:status IS NULL OR status = :status) AND MATCH(title, content) AGAINST(:q IN BOOLEAN MODE)",
            nativeQuery = true)
    Page<PostsEntity> searchFullTextOrderByCreatedAtDescWithStatus(@Param("q") String query,
                                                                  @Param("status") String status,
                                                                  Pageable pageable);

    @Query(value = "SELECT * FROM posts " +
            "WHERE is_deleted = 0 AND (:status IS NULL OR status = :status) " +
            "AND (title LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\' OR content LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\') " +
            "ORDER BY created_at DESC",
            countQuery = "SELECT COUNT(*) FROM posts " +
                    "WHERE is_deleted = 0 AND (:status IS NULL OR status = :status) " +
                    "AND (title LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\' OR content LIKE CONCAT('%', :kw, '%') ESCAPE '\\\\')",
            nativeQuery = true)
    Page<PostsEntity> searchLikeOrderByCreatedAtDescWithStatus(@Param("kw") String keyword,
                                                              @Param("status") String status,
                                                              Pageable pageable);

    @Query("select p.id from PostsEntity p where p.isDeleted = false and p.status = :status")
    List<Long> findIdsByStatusAndIsDeletedFalse(@Param("status") PostStatus status);

    java.util.Optional<PostsEntity> findByIdAndIsDeletedFalse(Long id);
}
