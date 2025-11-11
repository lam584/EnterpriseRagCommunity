package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSimilarHitsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ModerationSimilarHitsRepository extends JpaRepository<ModerationSimilarHitsEntity, Long>, JpaSpecificationExecutor<ModerationSimilarHitsEntity> {
    List<ModerationSimilarHitsEntity> findAllByContentTypeAndContentId(ContentType contentType, Long contentId);

    // 时间范围
    List<ModerationSimilarHitsEntity> findAllByMatchedAtBetween(LocalDateTime start, LocalDateTime end);

    // 相似度阈值过滤
    List<ModerationSimilarHitsEntity> findAllByDistanceLessThanEqualAndMatchedAtBetween(Double distance, LocalDateTime start, LocalDateTime end);
}

