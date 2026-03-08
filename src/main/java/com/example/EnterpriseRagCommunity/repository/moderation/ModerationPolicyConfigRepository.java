package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModerationPolicyConfigRepository extends JpaRepository<ModerationPolicyConfigEntity, Long> {
    Optional<ModerationPolicyConfigEntity> findByContentType(ContentType contentType);
}

