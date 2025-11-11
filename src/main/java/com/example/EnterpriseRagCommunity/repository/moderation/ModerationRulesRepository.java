package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ModerationRulesRepository extends JpaRepository<ModerationRulesEntity, Long>, JpaSpecificationExecutor<ModerationRulesEntity> {
    // 时间范围查询
    List<ModerationRulesEntity> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 按启用状态/类型/严重程度查询
    List<ModerationRulesEntity> findAllByEnabled(Boolean enabled);
    List<ModerationRulesEntity> findAllByType(RuleType type);
    List<ModerationRulesEntity> findAllBySeverity(Severity severity);

    // 组合条件示例
    List<ModerationRulesEntity> findAllByEnabledAndCreatedAtBetween(Boolean enabled, LocalDateTime start, LocalDateTime end);
}

