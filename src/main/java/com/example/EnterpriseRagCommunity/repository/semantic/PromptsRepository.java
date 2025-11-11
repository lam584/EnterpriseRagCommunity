package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromptsRepository extends JpaRepository<PromptsEntity, Long>, JpaSpecificationExecutor<PromptsEntity> {
    List<PromptsEntity> findByIsActive(Boolean isActive);
    List<PromptsEntity> findByName(String name);
    List<PromptsEntity> findByVersion(Integer version);

    List<PromptsEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    Optional<PromptsEntity> findTopByNameAndIsActiveOrderByVersionDesc(String name, Boolean isActive);
}

