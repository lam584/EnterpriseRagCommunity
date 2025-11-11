package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TotpSecretsRepository extends JpaRepository<TotpSecretsEntity, Long>, JpaSpecificationExecutor<TotpSecretsEntity> {
    List<TotpSecretsEntity> findByUserIdAndEnabledTrue(Long userId);
    Optional<TotpSecretsEntity> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
