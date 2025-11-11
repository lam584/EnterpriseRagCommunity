package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionsRepository extends JpaRepository<PermissionsEntity, Long>, JpaSpecificationExecutor<PermissionsEntity> {
    Optional<PermissionsEntity> findByResourceAndAction(String resource, String action);
    List<PermissionsEntity> findByResource(String resource);
}

