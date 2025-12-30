package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.FavoritesEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoritesRepository extends JpaRepository<FavoritesEntity, Long>, JpaSpecificationExecutor<FavoritesEntity> {
    Page<FavoritesEntity> findByUserId(Long userId, Pageable pageable);
    Page<FavoritesEntity> findByPostId(Long postId, Pageable pageable);

    boolean existsByUserIdAndPostId(Long userId, Long postId);
    long countByPostId(Long postId);
    void deleteByUserIdAndPostId(Long userId, Long postId);

    /**
     * 查询“我收藏的帖子”（按收藏时间倒序）。
     *
     * 注意：这里用 join favorites -> posts，保证书签页和取消收藏操作使用同一数据源。
     */
    @Query(
            value = "select p from PostsEntity p join FavoritesEntity f on f.postId = p.id " +
                    "where f.userId = :userId and p.isDeleted = false order by f.createdAt desc",
            countQuery = "select count(p.id) from PostsEntity p join FavoritesEntity f on f.postId = p.id " +
                    "where f.userId = :userId and p.isDeleted = false"
    )
    Page<PostsEntity> findBookmarkedPostsByUserId(@Param("userId") Long userId, Pageable pageable);
}
