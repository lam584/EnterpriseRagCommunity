package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.PostViewsDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PostViewsDailyRepository extends JpaRepository<PostViewsDailyEntity, PostViewsDailyEntity.Pk>, JpaSpecificationExecutor<PostViewsDailyEntity> {

    @Modifying
    @Query(value = "INSERT INTO post_views_daily(post_id, day, view_count, created_at, updated_at) " +
            "VALUES (:postId, :day, 1, NOW(3), NOW(3)) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW(3)",
            nativeQuery = true)
    void increment(@Param("postId") Long postId, @Param("day") LocalDate day);

    @Query(value = "SELECT pvd.post_id AS post_id, SUM(pvd.view_count) AS cnt " +
            "FROM post_views_daily pvd " +
            "WHERE pvd.day >= :fromDay AND pvd.day < :toDay " +
            "GROUP BY pvd.post_id", nativeQuery = true)
    List<Object[]> aggregateViewsBetweenDays(@Param("fromDay") LocalDate fromDay, @Param("toDay") LocalDate toDay);

    @Query(value = "SELECT pvd.post_id AS post_id, SUM(pvd.view_count) AS cnt " +
            "FROM post_views_daily pvd " +
            "GROUP BY pvd.post_id", nativeQuery = true)
    List<Object[]> aggregateViewsAll();

    @Query(value = "SELECT COALESCE(SUM(pvd.view_count), 0) " +
            "FROM post_views_daily pvd " +
            "WHERE pvd.post_id = :postId", nativeQuery = true)
    long sumViewsByPostId(@Param("postId") Long postId);
}

