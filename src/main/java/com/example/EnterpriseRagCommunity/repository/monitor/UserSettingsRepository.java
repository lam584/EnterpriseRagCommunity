package com.example.EnterpriseRagCommunity.repository.monitor;

import com.example.EnterpriseRagCommunity.entity.monitor.UserSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long>, JpaSpecificationExecutor<UserSettingsEntity> {
}

