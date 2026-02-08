package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesIndexConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationSamplesIndexConfigRepository extends JpaRepository<ModerationSamplesIndexConfigEntity, Long> {
}

