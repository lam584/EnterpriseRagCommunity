package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationPipelineRunRepository extends JpaRepository<ModerationPipelineRunEntity, Long> {
    Optional<ModerationPipelineRunEntity> findFirstByQueueIdOrderByCreatedAtDesc(Long queueId);
    Optional<ModerationPipelineRunEntity> findByTraceId(String traceId);

    Page<ModerationPipelineRunEntity> findAllByQueueIdOrderByCreatedAtDesc(Long queueId, Pageable pageable);

    Page<ModerationPipelineRunEntity> findAllByContentTypeAndContentIdOrderByCreatedAtDesc(ContentType contentType, Long contentId, Pageable pageable);

    List<ModerationPipelineRunEntity> findAllByQueueIdInOrderByCreatedAtDesc(Collection<Long> queueIds);

    @Modifying
    @Query("delete from ModerationPipelineRunEntity r where r.queueId = :queueId")
    int deleteAllByQueueId(@Param("queueId") Long queueId);
}
