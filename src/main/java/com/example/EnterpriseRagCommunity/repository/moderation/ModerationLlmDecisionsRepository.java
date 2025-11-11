package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmDecisionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationLlmDecisionsRepository extends JpaRepository<ModerationLlmDecisionsEntity, Long>, JpaSpecificationExecutor<ModerationLlmDecisionsEntity> {
    // 唯一查询：content_type + content_id + verdict
    Optional<ModerationLlmDecisionsEntity> findByContentTypeAndContentIdAndVerdict(ContentType contentType, Long contentId, Verdict verdict);

    // 时间范围
    List<ModerationLlmDecisionsEntity> findAllByDecidedAtBetween(LocalDateTime start, LocalDateTime end);

    // 组合：按内容/时间
    List<ModerationLlmDecisionsEntity> findAllByContentTypeAndContentIdAndDecidedAtBetween(ContentType contentType, Long contentId, LocalDateTime start, LocalDateTime end);
}
