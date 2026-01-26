package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostTagGenHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostTagGenHistoryRepository extends JpaRepository<PostTagGenHistoryEntity, Long> {
    Page<PostTagGenHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PostTagGenHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

