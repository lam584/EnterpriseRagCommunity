package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ReactionsRepository extends JpaRepository<ReactionsEntity, Long>, JpaSpecificationExecutor<ReactionsEntity> {
    Page<ReactionsEntity> findByUserId(Long userId, Pageable pageable);
    Page<ReactionsEntity> findByTargetTypeAndTargetId(ReactionTargetType targetType, Long targetId, Pageable pageable);
    Page<ReactionsEntity> findByTargetTypeAndTargetIdAndType(ReactionTargetType targetType, Long targetId, ReactionType type, Pageable pageable);

    boolean existsByUserIdAndTargetTypeAndTargetIdAndType(Long userId, ReactionTargetType targetType, Long targetId, ReactionType type);
    long countByTargetTypeAndTargetIdAndType(ReactionTargetType targetType, Long targetId, ReactionType type);
    void deleteByUserIdAndTargetTypeAndTargetIdAndType(Long userId, ReactionTargetType targetType, Long targetId, ReactionType type);
        @Transactional
        void deleteByUserIdAndTargetTypeAndType(Long userId, ReactionTargetType targetType, ReactionType type);

    @Query("""
            select r.targetId, count(r.id)
            from ReactionsEntity r
            where r.targetType = :targetType
              and r.type = :type
              and r.targetId in :targetIds
            group by r.targetId
            """)
    List<Object[]> countByTargetIdsGrouped(@Param("targetType") ReactionTargetType targetType,
                                          @Param("type") ReactionType type,
                                          @Param("targetIds") List<Long> targetIds);

    @Query("""
            select r.targetId
            from ReactionsEntity r
            where r.userId = :userId
              and r.targetType = :targetType
              and r.type = :type
              and r.targetId in :targetIds
            """)
    List<Long> findTargetIdsLikedByUser(@Param("userId") Long userId,
                                        @Param("targetType") ReactionTargetType targetType,
                                        @Param("type") ReactionType type,
                                        @Param("targetIds") List<Long> targetIds);

    @Query(
            value = "select p from PostsEntity p join ReactionsEntity r on r.targetId = p.id " +
                    "where r.userId = :userId and r.targetType = :targetType and r.type = :type and p.isDeleted = false " +
                    "order by r.createdAt desc",
            countQuery = "select count(p.id) from PostsEntity p join ReactionsEntity r on r.targetId = p.id " +
                    "where r.userId = :userId and r.targetType = :targetType and r.type = :type and p.isDeleted = false"
    )
    Page<PostsEntity> findBookmarkedPostsByUserId(@Param("userId") Long userId,
                                                 @Param("targetType") ReactionTargetType targetType,
                                                 @Param("type") ReactionType type,
                                                 Pageable pageable);
}
