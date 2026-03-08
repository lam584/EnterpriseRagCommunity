package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ModerationChunkSetRepository extends JpaRepository<ModerationChunkSetEntity, Long> {
    Optional<ModerationChunkSetEntity> findByQueueId(Long queueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ModerationChunkSetEntity s where s.id = :id")
    List<ModerationChunkSetEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from ModerationChunkSetEntity s where s.queueId in :queueIds")
    List<ModerationChunkSetEntity> findAllByQueueIds(@Param("queueIds") Collection<Long> queueIds);
}
