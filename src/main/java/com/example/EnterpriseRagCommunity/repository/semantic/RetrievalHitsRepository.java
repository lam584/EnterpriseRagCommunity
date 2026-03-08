package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.RetrievalHitsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.RetrievalHitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RetrievalHitsRepository extends JpaRepository<RetrievalHitsEntity, Long>, JpaSpecificationExecutor<RetrievalHitsEntity> {
    // by event FK
    List<RetrievalHitsEntity> findByEventId(Long eventId);

    // by event_id + rank
    List<RetrievalHitsEntity> findByEventIdAndRank(Long eventId, Integer rank);

    // by type
    List<RetrievalHitsEntity> findByHitType(RetrievalHitType hitType);

    // by post/chunk FKs
    List<RetrievalHitsEntity> findByPostId(Long postId);
    List<RetrievalHitsEntity> findByChunkId(Long chunkId);
}
