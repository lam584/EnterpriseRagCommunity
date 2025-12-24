package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.UserRolesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRolesRepository extends JpaRepository<UserRolesEntity, Long>, JpaSpecificationExecutor<UserRolesEntity> {
    List<UserRolesEntity> findByTenantId(Long tenantId);
    Optional<UserRolesEntity> findByTenantIdAndRoles(Long tenantId, String roles);
    List<UserRolesEntity> findByRolesContaining(String keyword);
    List<UserRolesEntity> findByCanLoginTrue();

    // Add missing method for existsByTenantIdAndRoles
    boolean existsByTenantIdAndRoles(Long tenantId, String roles);

    boolean existsByTenantIdAndRolesAndIdNot(Long tenantId, String roles, Long id);
}
