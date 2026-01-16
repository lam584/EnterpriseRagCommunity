package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.RolePermissionUpsertDTO;
import com.example.EnterpriseRagCommunity.dto.access.RolePermissionViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionId;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
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

        // 1) 清空旧配置
        rolePermissionsRepository.deleteAllByRoleId(roleId);

        if (dtoList == null || dtoList.isEmpty()) {
            // RBAC changed -> touch users with this role
            touchUsersByRoleId(roleId);
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
        return saved.stream().map(RolePermissionsService::toView).toList();
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

        // 校验 permissionId 都存在
        Set<Long> permIds = latestByPermId.keySet();
        long existingCount = permissionsRepository.countByIdIn(permIds);
        if (existingCount != permIds.size()) {
            throw new EntityNotFoundException("Some permissionId not found");
        }

        // 分配新的 roleId：max(role_id)+1（无记录时从 1 开始）
        Long maxRoleId = rolePermissionsRepository.findMaxRoleId();
        long nextRoleId = (maxRoleId == null ? 1L : (maxRoleId + 1L));

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
        return saved.stream().map(RolePermissionsService::toView).toList();
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

        RolePermissionsEntity entity = rolePermissionsRepository.findById(id).orElseGet(() -> {
            RolePermissionsEntity e = new RolePermissionsEntity();
            e.setRoleId(dto.getRoleId());
            e.setPermissionId(dto.getPermissionId());
            return e;
        });

        if (dto.getRoleName() != null && !dto.getRoleName().trim().isEmpty()) {
            entity.setRoleName(dto.getRoleName().trim());
        }
        entity.setAllow(dto.getAllow());
        RolePermissionsEntity saved = rolePermissionsRepository.save(entity);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(dto.getRoleId());
        return toView(saved);
    }

    @Transactional
    public void delete(Long roleId, Long permissionId) {
        RolePermissionId id = new RolePermissionId();
        id.setRoleId(roleId);
        id.setPermissionId(permissionId);

        if (!rolePermissionsRepository.existsById(id)) {
            throw new EntityNotFoundException("RolePermission not found: roleId=" + roleId + ", permissionId=" + permissionId);
        }

        rolePermissionsRepository.deleteById(id);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(roleId);
    }

    @Transactional
    public int clearRole(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId is required");
        }
        int n = rolePermissionsRepository.deleteAllByRoleId(roleId);
        // RBAC changed -> touch users with this role
        touchUsersByRoleId(roleId);
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
                u.setUpdatedAt(java.time.LocalDateTime.now());
            }
            usersRepository.saveAll(users);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public List<RoleInfoDTO> listRoles() {
        return rolePermissionsRepository.findRoleSummaries().stream()
                .map(v -> new RoleInfoDTO(v.getRoleId(), v.getRoleName()))
                .toList();
    }

    public record RoleInfoDTO(Long roleId, String roleName) {}
}
