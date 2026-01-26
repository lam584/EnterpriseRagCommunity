package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.SemanticTranslateHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SemanticTranslateHistoryRepository extends JpaRepository<SemanticTranslateHistoryEntity, Long> {
    Page<SemanticTranslateHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SemanticTranslateHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<SemanticTranslateHistoryEntity> findTopBySourceTypeAndSourceIdAndTargetLangAndSourceHashAndConfigHashOrderByCreatedAtDesc(
            String sourceType, Long sourceId, String targetLang, String sourceHash, String configHash
    );
}

