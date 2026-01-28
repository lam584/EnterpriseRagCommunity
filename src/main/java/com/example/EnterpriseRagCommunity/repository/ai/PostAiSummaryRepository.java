package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostAiSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostAiSummaryRepository extends JpaRepository<PostAiSummaryEntity, Long> {
    Optional<PostAiSummaryEntity> findByPostId(Long postId);
}

