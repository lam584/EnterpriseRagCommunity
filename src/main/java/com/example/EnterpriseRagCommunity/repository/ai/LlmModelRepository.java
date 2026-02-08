package com.example.EnterpriseRagCommunity.repository.ai;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;

@Repository
public interface LlmModelRepository extends JpaRepository<LlmModelEntity, Long> {
    Optional<LlmModelEntity> findByEnvAndProviderIdAndPurposeAndModelName(String env, String providerId, String purpose, String modelName);

    List<LlmModelEntity> findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(String env, String providerId);

    List<LlmModelEntity> findByEnvAndEnabledTrueAndModelNameInOrderByIsDefaultDescWeightDescIdAsc(String env, Collection<String> modelNames);

    List<LlmModelEntity> findByEnvAndPurposeAndEnabledTrueOrderByPriorityDescWeightDescIsDefaultDescIdAsc(String env, String purpose);

    List<LlmModelEntity> findByEnvOrderByPurposeAscPriorityDescWeightDescIsDefaultDescIdAsc(String env);

    List<LlmModelEntity> findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(String env, String purpose);

    List<LlmModelEntity> findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(String env);

    void deleteByEnvAndProviderId(String env, String providerId);
}
