package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LlmProviderRepository extends JpaRepository<LlmProviderEntity, Long> {
    Optional<LlmProviderEntity> findByEnvAndProviderId(String env, String providerId);

    List<LlmProviderEntity> findByEnvOrderByPriorityAscIdAsc(String env);

    List<LlmProviderEntity> findByEnvAndEnabledTrueOrderByPriorityAscIdAsc(String env);

    void deleteByEnvAndProviderId(String env, String providerId);
}
