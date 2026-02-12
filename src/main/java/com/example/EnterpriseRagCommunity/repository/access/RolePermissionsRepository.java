package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RolePermissionsRepository extends JpaRepository<RolePermissionsEntity, RolePermissionId>, JpaSpecificationExecutor<RolePermissionsEntity> {
    List<RolePermissionsEntity> findByRoleId(Long roleId);
    List<RolePermissionsEntity> findByRoleIdIn(Collection<Long> roleIds);
    List<RolePermissionsEntity> findByPermissionId(Long permissionId);
    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @Query("select distinct rp.roleId from RolePermissionsEntity rp order by rp.roleId asc")
    List<Long> findDistinctRoleIds();

    /**
     *

"Roles" are currently inferred from role_permissions.
     * This query returns one row per roleId with a representative roleName.
     * - roleName may be null if never set.
     * - we use max(roleName) to get a deterministic value when multiple rows exist.
     */
    @Query("select rp.roleId as roleId, max(rp.roleName) as roleName " +
            "from RolePermissionsEntity rp group by rp.roleId order by rp.roleId asc")
    List<RoleSummaryView> findRoleSummaries();

    interface RoleSummaryView {
        Long getRoleId();
        String getRoleName();
    }

    @Query("select max(rp.roleId) from RolePermissionsEntity rp")
    Long findMaxRoleId();

    @Modifying
    @Query("delete from RolePermissionsEntity rp where rp.roleId = :roleId")
    int deleteAllByRoleId(@Param("roleId") Long roleId);
}
