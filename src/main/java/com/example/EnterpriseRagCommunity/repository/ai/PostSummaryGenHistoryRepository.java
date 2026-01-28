package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostSummaryGenHistoryRepository extends JpaRepository<PostSummaryGenHistoryEntity, Long> {
    Page<PostSummaryGenHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<PostSummaryGenHistoryEntity> findByPostIdOrderByCreatedAtDesc(Long postId, Pageable pageable);
}
