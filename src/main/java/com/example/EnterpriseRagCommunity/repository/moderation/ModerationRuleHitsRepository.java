package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRuleHitsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ModerationRuleHitsRepository extends JpaRepository<ModerationRuleHitsEntity, Long>, JpaSpecificationExecutor<ModerationRuleHitsEntity> {
    // 基本按内容和规则查询
    List<ModerationRuleHitsEntity> findAllByContentTypeAndContentId(ContentType contentType, Long contentId);
    List<ModerationRuleHitsEntity> findAllByRuleId(Long ruleId);

    // 时间范围
    List<ModerationRuleHitsEntity> findAllByMatchedAtBetween(LocalDateTime start, LocalDateTime end);

    // 组合条件：内容+时间
    List<ModerationRuleHitsEntity> findAllByContentTypeAndContentIdAndMatchedAtBetween(ContentType contentType, Long contentId, LocalDateTime start, LocalDateTime end);
}
