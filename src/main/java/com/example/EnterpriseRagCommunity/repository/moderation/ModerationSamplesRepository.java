package com.example.EnterpriseRagCommunity.repository.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModerationSamplesRepository extends JpaRepository<ModerationSamplesEntity, Long>, JpaSpecificationExecutor<ModerationSamplesEntity> {

    Optional<ModerationSamplesEntity> findByTextHash(String textHash);

    List<ModerationSamplesEntity> findByEnabled(Boolean enabled);

    List<ModerationSamplesEntity> findByCategoryAndEnabled(ModerationSamplesEntity.Category category, Boolean enabled);
}
