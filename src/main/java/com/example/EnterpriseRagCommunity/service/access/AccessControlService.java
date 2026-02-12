package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.entity.access.PermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolePermissionsEntity;
import com.example.EnterpriseRagCommunity.entity.access.RolesEntity;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Loads roles and permissions for a user and builds Spring Security authorities.
 *
 * Authorities produced:
 * - ROLE_{roleName} (for hasRole/hasAuthority; roleName from user_roles.roles)
 * - ROLE_ID_{roleId} (compat/debug)
 * - PERM_{resource}:{action} (for hasAuthority)
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    public static final String PERM_PREFIX = "PERM_";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_ID_PREFIX = "ROLE_ID_";

    private final UsersRepository usersRepository;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final RolePermissionsRepository rolePermissionsRepository;
    private final PermissionsRepository permissionsRepository;
    private final RolesRepository rolesRepository;

    /**
     * If true: deny (allow=false) wins over allow.
     * If false: treat allow=false as not granted (ignore).
     */
    @Value("${security.permissions.deny-first:true}")
    private boolean denyFirst;

    @Transactional(readOnly = true)
    public UsersEntity loadActiveUserByEmail(String email) {
        return usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));
    }

    /**
     * Build authorities in one transaction.
     */
    @Transactional(readOnly = true)
    public List<GrantedAuthority> buildAuthorities(Long userId) {
        AccessData data = loadAccessData(userId);

        List<GrantedAuthority> authorities = new ArrayList<>();

        // roles -> ROLE_{name} + ROLE_ID_{id}
        if (!data.assignments.isEmpty()) {
            Map<Long, String> roleIdToName = new HashMap<>();

            Set<Long> roleIds = new LinkedHashSet<>();
            for (RoleAssignment a : data.assignments) {
                if (a == null || a.roleId == null) continue;
                roleIds.add(a.roleId);
            }

            List<RolesEntity> roles = rolesRepository.findAllById(roleIds);
            for (RolesEntity r : roles) {
                if (r == null) continue;
                String roleName = r.getRoleName();
                if (roleName != null && !roleName.isBlank()) {
                    roleIdToName.put(r.getRoleId(), roleName.trim());
                }
            }

            for (Long roleId : roleIds) {
                if (roleId == null) continue;
                if (roleIdToName.containsKey(roleId)) continue;
                List<RolePermissionsEntity> rps = rolePermissionsRepository.findByRoleId(roleId);
                for (RolePermissionsEntity rp : rps) {
                    String roleName = rp.getRoleName();
                    if (roleName != null && !roleName.isBlank()) {
                        roleIdToName.put(roleId, roleName.trim());
                        break;
                    }
                }
            }

            for (RoleAssignment a : data.assignments) {
                if (a == null || a.roleId == null) continue;
                String suffix = scopeSuffix(a.scopeType, a.scopeId);

                authorities.add(new SimpleGrantedAuthority(ROLE_ID_PREFIX + a.roleId + suffix));

                String roleName = roleIdToName.get(a.roleId);
                if (roleName != null && !roleName.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleName + suffix));
                }
            }
        }

        // permissions -> PERM_resource:action
        for (String key : data.permissionKeys) {
            authorities.add(new SimpleGrantedAuthority(PERM_PREFIX + key));
        }

        return authorities;
    }

    private AccessData loadAccessData(Long userId) {
        Set<RoleAssignment> assignments = new LinkedHashSet<>();
        Set<String> permissionKeys = new LinkedHashSet<>();

        LocalDateTime now = LocalDateTime.now();
        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        if (links.isEmpty()) {
            return new AccessData(assignments, permissionKeys);
        }

        for (UserRoleLinksEntity link : links) {
            if (link == null || link.getRoleId() == null) continue;
            if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(now)) continue;
            String scopeType = (link.getScopeType() == null || link.getScopeType().isBlank()) ? "GLOBAL" : link.getScopeType().trim().toUpperCase(Locale.ROOT);
            Long scopeId = link.getScopeId() == null ? 0L : link.getScopeId();
            assignments.add(new RoleAssignment(link.getRoleId(), scopeType, scopeId));
        }

        if (assignments.isEmpty()) {
            return new AccessData(assignments, permissionKeys);
        }

        Map<ScopeKey, Set<Long>> scopeToRoleIds = new LinkedHashMap<>();
        Set<Long> allRoleIds = new LinkedHashSet<>();
        for (RoleAssignment a : assignments) {
            allRoleIds.add(a.roleId);
            ScopeKey key = new ScopeKey(a.scopeType, a.scopeId);
            scopeToRoleIds.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(a.roleId);
        }

        List<RolePermissionsEntity> allRolePerms = rolePermissionsRepository.findByRoleIdIn(allRoleIds);
        Map<Long, List<RolePermissionsEntity>> roleIdToPerms = new HashMap<>();
        for (RolePermissionsEntity rp : allRolePerms) {
            if (rp == null || rp.getRoleId() == null) continue;
            roleIdToPerms.computeIfAbsent(rp.getRoleId(), k -> new ArrayList<>()).add(rp);
        }

        Map<Long, String> permIdToKey = new HashMap<>();
        Set<Long> allowIdsAllScopes = new LinkedHashSet<>();

        Map<ScopeKey, Set<Long>> scopeAllowIds = new LinkedHashMap<>();
        for (var entry : scopeToRoleIds.entrySet()) {
            ScopeKey scope = entry.getKey();
            Set<Long> roleIds = entry.getValue();
            Set<Long> allowIds = new LinkedHashSet<>();
            Set<Long> denyIds = new LinkedHashSet<>();

            for (Long roleId : roleIds) {
                List<RolePermissionsEntity> rps = roleIdToPerms.getOrDefault(roleId, List.of());
                for (RolePermissionsEntity rp : rps) {
                    if (rp.getPermissionId() == null) continue;
                    if (Boolean.FALSE.equals(rp.getAllow())) denyIds.add(rp.getPermissionId());
                    else allowIds.add(rp.getPermissionId());
                }
            }

            if (denyFirst) {
                allowIds.removeAll(denyIds);
            }
            scopeAllowIds.put(scope, allowIds);
            allowIdsAllScopes.addAll(allowIds);
        }

        if (!allowIdsAllScopes.isEmpty()) {
            for (PermissionsEntity p : permissionsRepository.findAllById(allowIdsAllScopes)) {
                if (p == null || p.getId() == null) continue;
                permIdToKey.put(p.getId(), toPermissionKey(p.getResource(), p.getAction()));
            }
        }

        for (var entry : scopeAllowIds.entrySet()) {
            ScopeKey scope = entry.getKey();
            for (Long permId : entry.getValue()) {
                String baseKey = permIdToKey.get(permId);
                if (baseKey == null || baseKey.isBlank()) continue;
                permissionKeys.add(baseKey + scopeSuffix(scope.scopeType, scope.scopeId));
            }
        }

        return new AccessData(assignments, permissionKeys);
    }

    public static String toPermissionKey(String resource, String action) {
        return (resource == null ? "" : resource.trim()) + ":" + (action == null ? "" : action.trim());
    }

    private static String scopeSuffix(String scopeType, Long scopeId) {
        String st = (scopeType == null || scopeType.isBlank()) ? "GLOBAL" : scopeType.trim().toUpperCase(Locale.ROOT);
        long sid = scopeId == null ? 0L : scopeId;
        if ("GLOBAL".equals(st) && sid == 0L) return "";
        return "@" + st + ":" + sid;
    }

    private record ScopeKey(String scopeType, Long scopeId) {
    }

    private record RoleAssignment(Long roleId, String scopeType, Long scopeId) {
    }

    private record AccessData(Set<RoleAssignment> assignments, Set<String> permissionKeys) {
    }
}
