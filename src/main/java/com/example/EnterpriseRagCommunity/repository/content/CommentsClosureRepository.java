package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.CommentsClosureEntity;
import com.example.EnterpriseRagCommunity.entity.content.id.CommentsClosureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentsClosureRepository extends JpaRepository<CommentsClosureEntity, CommentsClosureId>, JpaSpecificationExecutor<CommentsClosureEntity> {
    List<CommentsClosureEntity> findByAncestorId(Long ancestorId);
    List<CommentsClosureEntity> findByDescendantId(Long descendantId);
}
