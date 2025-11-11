package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionsRepository extends JpaRepository<RolePermissionsEntity, RolePermissionId>, JpaSpecificationExecutor<RolePermissionsEntity> {
    List<RolePermissionsEntity> findByRoleId(Long roleId);
    List<RolePermissionsEntity> findByPermissionId(Long permissionId);
    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
