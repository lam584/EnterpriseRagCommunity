package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.SupportedLanguageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportedLanguageRepository extends JpaRepository<SupportedLanguageEntity, Long> {
    List<SupportedLanguageEntity> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    Optional<SupportedLanguageEntity> findByLanguageCode(String languageCode);

    Optional<SupportedLanguageEntity> findByDisplayName(String displayName);

    Optional<SupportedLanguageEntity> findTopByIsActiveTrueOrderBySortOrderDescIdDesc();
}
