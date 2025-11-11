package com.example.EnterpriseRagCommunity.repository.content;

import com.example.EnterpriseRagCommunity.entity.content.HotScoresEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotScoresRepository extends JpaRepository<HotScoresEntity, Long>, JpaSpecificationExecutor<HotScoresEntity> {
    Page<HotScoresEntity> findAllByOrderByScore24hDesc(Pageable pageable);
    Page<HotScoresEntity> findAllByOrderByScore7dDesc(Pageable pageable);
    Page<HotScoresEntity> findAllByOrderByScoreAllDesc(Pageable pageable);
    List<HotScoresEntity> findByPostIdIn(List<Long> postIds);
}
