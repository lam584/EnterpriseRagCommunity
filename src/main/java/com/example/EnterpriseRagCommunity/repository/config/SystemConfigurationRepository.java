package com.example.EnterpriseRagCommunity.repository.config;

import com.example.EnterpriseRagCommunity.entity.config.SystemConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfigurationEntity, String> {
}
