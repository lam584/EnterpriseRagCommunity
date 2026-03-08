package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModerationConfidenceFallbackConfigRepository extends JpaRepository<ModerationConfidenceFallbackConfigEntity, Long> {
    Optional<ModerationConfidenceFallbackConfigEntity> findFirstByOrderByUpdatedAtDescIdDesc();
}
