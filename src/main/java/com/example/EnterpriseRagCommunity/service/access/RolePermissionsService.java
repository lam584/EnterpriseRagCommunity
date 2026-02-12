package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.RolePermissionUpsertDTO;
import com.example.EnterpriseRagCommunity.dto.access.RolePermissionViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RolePermissionsService {

    private final RolePermissionsRepository rolePermissionsRepository;
    private final PermissionsRepository permissionsRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final UsersRepository usersRepository;
    private final RbacAuditService rbacAuditService;
    private final RolesRepository rolesRepository;

    private static RolePermissionViewDTO toView(RolePermissionsEntity e) {
        if (e == null) return null;
        return new RolePermissionViewDTO(e.getRoleId(), e.getRoleName(), e.getPermissionId(), e.getAllow());
    }

    @Transactional(readOnly = true)
    public List<Long> listRoleIds() {
        return rolePermissionsRepository.findDistinctRoleIds();
    }

    @Transactional(readOnly = true)
    public List<RolePermissionViewDTO> listByRoleId(Long roleId) {
        return rolePermissionsRepository.findByRoleId(roleId)
                .stream()
                .map(RolePermissionsService::toView)
                .toList();
    }

    /**
     * 覆盖式更新：提交 roleId 下的完整配置（allow/deny）。
     * - 若 dtoList 为空：表示清空该 roleId 的全部权限配置。
     * - dtoList 中若存在重复 permissionId：后者覆盖前者。
     */
    @Transactional
    public List<RolePermissionViewDTO> replaceAllForRole(Long roleId, List<RolePermissionUpsertDTO> dtoList) {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId is required");
        }
        List<RolePermissionViewDTO> before = listByRoleId(roleId);

        // 1) 清空旧配置
        rolePermissionsRepository.deleteAllByRoleId(roleId);

        if (dtoList == null || dtoList.isEmpty()) {
            // RBAC changed -> touch users with this role
            touchUsersByRoleId(roleId);
            rbacAuditService.record("ROLE_MATRIX_REPLACE", "role_permissions", "roleId=" + roleId, before, List.of());
            return List.of();
        }

        // 2) 规范化：只接受 roleId 一致的数据，并对 permissionId 去重
        String roleName = null;
        Map<Long, RolePermissionUpsertDTO> latestByPermId = new LinkedHashMap<>();
        for (RolePermissionUpsertDTO dto : dtoList) {
            if (dto == null) continue;
            if (dto.getPermissionId() == null) {
                throw new IllegalArgumentException("permissionId is required");
            }
            if (dto.getAllow() == null) {
                throw new IllegalArgumentException("allow is required");
            }
            // roleId 以 path 为准
            dto.setRoleId(roleId);

            // roleName：取第一个非空/非空白的作为本次写入的统一值
            if (roleName == null && dto.getRoleName() != null && !dto.getRoleName().trim().isEmpty()) {
                roleName = dto.getRoleName().trim();
            }

            latestByPermId.put(dto.getPermissionId(), dto);
        }

        if (roleName != null && !roleName.isBlank()) {
            upsertRoleMeta(roleId, roleName.trim());
        }

        // 3) 校验 permissionId 都存在
        Set<Long> permIds = latestByPermId.keySet();
        long existingCount = permissionsRepository.countByIdIn(permIds);
        if (existingCount != permIds.size()) {
            throw new EntityNotFoundException("Some permissionId not found");
        }

        // 4) 批量写入
        List<RolePermissionsEntity> entities = new ArrayList<>();
        for (RolePermissionUpsertDTO dto : latestByPermId.values()) {
            RolePermissionsEntity e = new RolePermissionsEntity();
            e.setRoleId(roleId);
            e.setRoleName(roleName);
            e.setPermissionId(dto.getPermissionId());
            e.setAllow(dto.getAllow());
            entities.add(e);
        }

        List<RolePermissionsEntity> saved = rolePermissionsRepository.saveAll(entities);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(roleId);
        List<RolePermissionViewDTO> after = saved.stream().map(RolePermissionsService::toView).toList();
        rbacAuditService.record("ROLE_MATRIX_REPLACE", "role_permissions", "roleId=" + roleId, before, after);
        return after;
    }

    /**
     * 新建角色并写入权限矩阵（roleId 自动分配）。
     * 说明：当前系统未使用独立的 user_roles 表，roleId 仅存在于 role_permissions 里。
     */
    @Transactional
    public List<RolePermissionViewDTO> createRoleWithMatrix(List<RolePermissionUpsertDTO> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            throw new IllegalArgumentException("Permission matrix is required");
        }

        // roleName：取第一个非空/非空白的作为统一值（允许为空）
        String roleName = null;
        Map<Long, RolePermissionUpsertDTO> latestByPermId = new LinkedHashMap<>();
        for (RolePermissionUpsertDTO dto : dtoList) {
            if (dto == null) continue;
            if (dto.getPermissionId() == null) {
                throw new IllegalArgumentException("permissionId is required");
            }
            if (dto.getAllow() == null) {
                throw new IllegalArgumentException("allow is required");
            }
            if (roleName == null && dto.getRoleName() != null && !dto.getRoleName().trim().isEmpty()) {
                roleName = dto.getRoleName().trim();
            }
            latestByPermId.put(dto.getPermissionId(), dto);
        }

        if (latestByPermId.isEmpty()) {
            throw new IllegalArgumentException("Permission matrix is required");
        }
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("roleName is required");
        }

        // 校验 permissionId 都存在
        Set<Long> permIds = latestByPermId.keySet();
        long existingCount = permissionsRepository.countByIdIn(permIds);
        if (existingCount != permIds.size()) {
            throw new EntityNotFoundException("Some permissionId not found");
        }

        // 分配新的 roleId：max(role_id)+1（无记录时从 1 开始）
        Long maxRoleId = rolePermissionsRepository.findMaxRoleId();
        long nextRoleId = (maxRoleId == null ? 1L : (maxRoleId + 1L));
        upsertRoleMeta(nextRoleId, roleName.trim());

        List<RolePermissionsEntity> entities = new ArrayList<>();
        for (RolePermissionUpsertDTO dto : latestByPermId.values()) {
            RolePermissionsEntity e = new RolePermissionsEntity();
            e.setRoleId(nextRoleId);
            e.setRoleName(roleName);
            e.setPermissionId(dto.getPermissionId());
            e.setAllow(dto.getAllow());
            entities.add(e);
        }

        List<RolePermissionsEntity> saved = rolePermissionsRepository.saveAll(entities);
        // New role created; no users yet, but harmless to touch if somebody links later.
        touchUsersByRoleId(nextRoleId);
        List<RolePermissionViewDTO> after = saved.stream().map(RolePermissionsService::toView).toList();
        rbacAuditService.record("ROLE_CREATE_WITH_MATRIX", "role_permissions", "roleId=" + nextRoleId, null, after);
        return after;
    }

    @Transactional
    public RolePermissionViewDTO upsert(RolePermissionUpsertDTO dto) {
        // roleId 不再校验是否存在于 user_roles：历史表已删除。
        if (dto.getRoleId() == null) {
            throw new EntityNotFoundException("roleId is required");
        }
        if (!permissionsRepository.existsById(dto.getPermissionId())) {
            throw new EntityNotFoundException("Permission not found: " + dto.getPermissionId());
        }

        RolePermissionId id = new RolePermissionId();
        id.setRoleId(dto.getRoleId());
        id.setPermissionId(dto.getPermissionId());

        RolePermissionsEntity existing = rolePermissionsRepository.findById(id).orElse(null);
        RolePermissionViewDTO before = toView(existing);
        RolePermissionsEntity entity = existing != null ? existing : new RolePermissionsEntity();
        if (existing == null) {
            RolePermissionsEntity e = new RolePermissionsEntity();
            e.setRoleId(dto.getRoleId());
            e.setPermissionId(dto.getPermissionId());
            entity = e;
        }

        if (dto.getRoleName() != null && !dto.getRoleName().trim().isEmpty()) {
            String trimmed = dto.getRoleName().trim();
            entity.setRoleName(trimmed);
            upsertRoleMeta(dto.getRoleId(), trimmed);
        }
        entity.setAllow(dto.getAllow());
        RolePermissionsEntity saved = rolePermissionsRepository.save(entity);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(dto.getRoleId());
        RolePermissionViewDTO after = toView(saved);
        rbacAuditService.record("ROLE_PERMISSION_UPSERT", "role_permissions", "roleId=" + dto.getRoleId() + ",permissionId=" + dto.getPermissionId(), before, after);
        return after;
    }

    @Transactional
    public void delete(Long roleId, Long permissionId) {
        RolePermissionId id = new RolePermissionId();
        id.setRoleId(roleId);
        id.setPermissionId(permissionId);

        RolePermissionsEntity existing = rolePermissionsRepository.findById(id).orElseThrow(() ->
                new EntityNotFoundException("RolePermission not found: roleId=" + roleId + ", permissionId=" + permissionId));
        RolePermissionViewDTO before = toView(existing);
        rolePermissionsRepository.deleteById(id);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(roleId);
        rbacAuditService.record("ROLE_PERMISSION_DELETE", "role_permissions", "roleId=" + roleId + ",permissionId=" + permissionId, before, null);
    }

    @Transactional
    public int clearRole(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId is required");
        }
        List<RolePermissionViewDTO> before = listByRoleId(roleId);
        int n = rolePermissionsRepository.deleteAllByRoleId(roleId);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(roleId);
        rbacAuditService.record("ROLE_CLEAR", "role_permissions", "roleId=" + roleId, before, List.of());
        return n;
    }

    private void touchUsersByRoleId(Long roleId) {
        if (roleId == null) return;
        try {
            var links = userRoleLinksRepository.findByRoleId(roleId);
            if (links == null || links.isEmpty()) return;
            var userIds = links.stream().map(l -> l.getUserId()).filter(Objects::nonNull).distinct().toList();
            if (userIds.isEmpty()) return;
            List<UsersEntity> users = usersRepository.findAllById(userIds);
            if (users.isEmpty()) return;
            // Trigger JPA @PreUpdate -> updates updated_at
            for (UsersEntity u : users) {
                long v = u.getAccessVersion() == null ? 0L : u.getAccessVersion();
                u.setAccessVersion(v + 1L);
                u.setUpdatedAt(java.time.LocalDateTime.now());
            }
            usersRepository.saveAll(users);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public List<RoleInfoDTO> listRoles() {
        Map<Long, String> map = new HashMap<>();
        for (RolesEntity r : rolesRepository.listAll()) {
            if (r == null || r.getRoleId() == null) continue;
            String name = r.getRoleName();
            if (name != null && !name.isBlank()) {
                map.put(r.getRoleId(), name.trim());
            }
        }
        for (var v : rolePermissionsRepository.findRoleSummaries()) {
            if (v.getRoleId() == null) continue;
            if (map.containsKey(v.getRoleId())) continue;
            String name = v.getRoleName();
            if (name != null && !name.isBlank()) map.put(v.getRoleId(), name.trim());
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new RoleInfoDTO(e.getKey(), e.getValue()))
                .toList();
    }

    public record RoleInfoDTO(Long roleId, String roleName) {}

    private void upsertRoleMeta(Long roleId, String roleName) {
        if (roleId == null) return;
        if (roleName == null || roleName.isBlank()) return;
        RolesEntity entity = rolesRepository.findById(roleId).orElseGet(() -> {
            RolesEntity r = new RolesEntity();
            r.setRoleId(roleId);
            r.setRiskLevel("LOW");
            r.setBuiltin(Boolean.FALSE);
            r.setImmutable(Boolean.FALSE);
            r.setCreatedAt(java.time.LocalDateTime.now());
            r.setUpdatedAt(java.time.LocalDateTime.now());
            return r;
        });
        entity.setRoleName(roleName.trim());
        if (entity.getRiskLevel() == null || entity.getRiskLevel().isBlank()) entity.setRiskLevel("LOW");
        if (entity.getBuiltin() == null) entity.setBuiltin(Boolean.FALSE);
        if (entity.getImmutable() == null) entity.setImmutable(Boolean.FALSE);
        if (entity.getCreatedAt() == null) entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        rolesRepository.save(entity);
    }
}
