package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.HotScoresEntity;
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
public interface HotScoresRepository extends JpaRepository<HotScoresEntity, Long>, JpaSpecificationExecutor<HotScoresEntity> {
    Page<HotScoresEntity> findAllByOrderByScore24hDesc(Pageable pageable);
    Page<HotScoresEntity> findAllByOrderByScore7dDesc(Pageable pageable);
    Page<HotScoresEntity> findAllByOrderByScoreAllDesc(Pageable pageable);
    List<HotScoresEntity> findByPostIdIn(List<Long> postIds);

    // =============== Aggregations for natural-day windows ===============

    /**
     * Likes over a time range (inclusive-exclusive).
     * reactions.target_type/type are enums stored as varchar.
     */
    @Query(value = "SELECT r.target_id AS post_id, COUNT(*) AS cnt " +
            "FROM reactions r " +
            "JOIN posts p ON p.id = r.target_id " +
            "WHERE p.is_deleted = 0 AND p.status = 'PUBLISHED' " +
            "  AND r.target_type = 'POST' AND r.type = 'LIKE' " +
            "  AND r.created_at >= :from AND r.created_at < :to " +
            "GROUP BY r.target_id", nativeQuery = true)
    List<Object[]> aggregateLikesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT f.post_id AS post_id, COUNT(*) AS cnt " +
            "FROM favorites f " +
            "JOIN posts p ON p.id = f.post_id " +
            "WHERE p.is_deleted = 0 AND p.status = 'PUBLISHED' " +
            "  AND f.created_at >= :from AND f.created_at < :to " +
            "GROUP BY f.post_id", nativeQuery = true)
    List<Object[]> aggregateFavoritesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT c.post_id AS post_id, COUNT(*) AS cnt " +
            "FROM comments c " +
            "JOIN posts p ON p.id = c.post_id " +
            "WHERE p.is_deleted = 0 AND p.status = 'PUBLISHED' " +
            "  AND c.is_deleted = 0 AND c.status = 'VISIBLE' " +
            "  AND c.created_at >= :from AND c.created_at < :to " +
            "GROUP BY c.post_id", nativeQuery = true)
    List<Object[]> aggregateCommentsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // =============== All-time aggregations ===============

    @Query(value = "SELECT r.target_id AS post_id, COUNT(*) AS cnt " +
            "FROM reactions r " +
            "JOIN posts p ON p.id = r.target_id " +
            "WHERE p.is_deleted = 0 AND p.status = 'PUBLISHED' " +
            "  AND r.target_type = 'POST' AND r.type = 'LIKE' " +
            "GROUP BY r.target_id", nativeQuery = true)
    List<Object[]> aggregateLikesAll();

    @Query(value = "SELECT f.post_id AS post_id, COUNT(*) AS cnt " +
            "FROM favorites f " +
            "JOIN posts p ON p.id = f.post_id " +
            "WHERE p.is_deleted = 0 AND p.status = 'PUBLISHED' " +
            "GROUP BY f.post_id", nativeQuery = true)
    List<Object[]> aggregateFavoritesAll();

    @Query(value = "SELECT c.post_id AS post_id, COUNT(*) AS cnt " +
            "FROM comments c " +
            "JOIN posts p ON p.id = c.post_id " +
            "WHERE p.is_deleted = 0 AND p.status = 'PUBLISHED' " +
            "  AND c.is_deleted = 0 AND c.status = 'VISIBLE' " +
            "GROUP BY c.post_id", nativeQuery = true)
    List<Object[]> aggregateCommentsAll();

    Page<HotScoresEntity> findAllByScore24hGreaterThanOrderByScore24hDesc(Double minScore, Pageable pageable);
    Page<HotScoresEntity> findAllByScore7dGreaterThanOrderByScore7dDesc(Double minScore, Pageable pageable);
    Page<HotScoresEntity> findAllByScoreAllGreaterThanOrderByScoreAllDesc(Double minScore, Pageable pageable);
}
