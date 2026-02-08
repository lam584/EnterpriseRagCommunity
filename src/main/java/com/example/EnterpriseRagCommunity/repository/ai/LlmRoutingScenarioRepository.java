package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmRoutingScenarioRepository extends JpaRepository<LlmRoutingScenarioEntity, String> {
    List<LlmRoutingScenarioEntity> findAllByOrderBySortIndexAsc();
}
