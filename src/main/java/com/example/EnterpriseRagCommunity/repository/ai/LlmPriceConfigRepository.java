package com.example.EnterpriseRagCommunity.repository.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmPriceConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LlmPriceConfigRepository extends JpaRepository<LlmPriceConfigEntity, Long> {
    Optional<LlmPriceConfigEntity> findByName(String name);

    List<LlmPriceConfigEntity> findByNameIn(Collection<String> names);

    List<LlmPriceConfigEntity> findByIdIn(Collection<Long> ids);
}
