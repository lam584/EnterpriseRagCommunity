package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReactionsRepository extends JpaRepository<ReactionsEntity, Long>, JpaSpecificationExecutor<ReactionsEntity> {
    Page<ReactionsEntity> findByUserId(Long userId, Pageable pageable);
    Page<ReactionsEntity> findByTargetTypeAndTargetId(ReactionTargetType targetType, Long targetId, Pageable pageable);
    Page<ReactionsEntity> findByTargetTypeAndTargetIdAndType(ReactionTargetType targetType, Long targetId, ReactionType type, Pageable pageable);
}
