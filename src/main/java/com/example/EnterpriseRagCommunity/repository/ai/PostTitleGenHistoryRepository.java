package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.PostTitleGenHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostTitleGenHistoryRepository extends JpaRepository<PostTitleGenHistoryEntity, Long> {
    Page<PostTitleGenHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PostTitleGenHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}

