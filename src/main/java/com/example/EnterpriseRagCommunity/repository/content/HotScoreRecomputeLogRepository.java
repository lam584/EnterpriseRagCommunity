package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.HotScoreRecomputeLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HotScoreRecomputeLogRepository extends JpaRepository<HotScoreRecomputeLogEntity, Long> {
    Page<HotScoreRecomputeLogEntity> findAllByOrderByIdDesc(Pageable pageable);
}
